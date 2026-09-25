package com.domus.api.modules.anexo;

import com.domus.api.modules.anexo.dto.AnexoUploadResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.armazenamento.ArmazenamentoR2;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/** Upload, leitura e remoção de anexos genéricos (boletos, NFes, comprovantes).
 *  Armazena os bytes no R2 (mesmo bucket de fotos, prefixo `anexos/`). */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnexoService {

    private static final String PREFIXO = "anexos/";
    private static final long LIMITE_BYTES = 30 * 1024 * 1024; // 30 MB

    private final AnexoRepository repository;
    private final ArmazenamentoR2 armazenamento;

    @Transactional
    public AnexoUploadResponse upload(MultipartFile arquivo, UUID igrejaId) {
        byte[] conteudo = lerBytes(arquivo);
        if (conteudo.length > LIMITE_BYTES) {
            throw new BusinessException("ARQUIVO_MUITO_GRANDE",
                    "Arquivo excede o limite de 30 MB.");
        }

        String tipo = arquivo.getContentType();
        if (tipo == null || tipo.isBlank()) {
            tipo = "application/octet-stream";
        }

        // Chave aleatória com prefixo para isolar dos outros conteúdos.
        String chave = PREFIXO + igrejaId + "/" + UUID.randomUUID();

        armazenamento.guardar(chave, conteudo, tipo);

        Anexo anexo = Anexo.builder()
                .igreja(Igreja.builder().id(igrejaId).build())
                .chave(chave)
                .tipo(tipo)
                .bytes((long) conteudo.length)
                .nomeOriginal(arquivo.getOriginalFilename() != null
                        ? arquivo.getOriginalFilename()
                        : "arquivo")
                .build();
        anexo = repository.save(anexo);

        log.info("Anexo enviado. anexo_id={}, igreja_id={}, bytes={}", anexo.getId(), igrejaId, conteudo.length);
        return AnexoUploadResponse.from(anexo);
    }

    @Transactional(readOnly = true)
    public byte[] ler(UUID id, UUID igrejaId) {
        Anexo anexo = repository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Anexo não encontrado."));
        return armazenamento.ler(anexo.getChave());
    }

    /** Busca o anexo validando que pertence à igreja — para vinculação. */
    @Transactional(readOnly = true)
    public Anexo buscarParaVincular(UUID anexoId, UUID igrejaId) {
        if (anexoId == null) return null;
        return repository.findByIdAndIgrejaId(anexoId, igrejaId)
                .orElseThrow(() -> new BusinessException("ANEXO_INVALIDO",
                        "Anexo não encontrado ou não pertence a esta igreja."));
    }

    @Transactional
    public void remover(UUID id) {
        Anexo anexo = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anexo não encontrado."));
        String chave = anexo.getChave();

        repository.delete(anexo);
        apagarArquivoAposCommit(chave, id);
    }

    private void apagarArquivoAposCommit(String chave, UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            armazenamento.remover(chave);
            log.info("Anexo removido. anexo_id={}", id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                armazenamento.remover(chave);
                log.info("Anexo removido. anexo_id={}", id);
            }
        });
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO",
                    "Não foi possível ler o arquivo enviado.");
        }
    }
}
