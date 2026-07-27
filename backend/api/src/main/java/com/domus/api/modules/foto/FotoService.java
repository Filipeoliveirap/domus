package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.armazenamento.ArmazenamentoFotos;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Envio e leitura de fotos. Os bytes vivem no bucket privado; aqui só o metadado.
 * Toda foto gera três versões: original (nunca servido), display (1200px) e thumb (200px).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FotoService {

    private final FotoRepository fotoRepository;
    private final ArmazenamentoFotos armazenamentoFotos;
    private final ProcessadorImagem processadorImagem;

    /**
     * Guarda os bytes no bucket antes de gravar a linha no banco. Se o storage falhar,
     * não sobra linha órfã; se o banco falhar depois, a rotina de limpeza resolve os
     * órfãos do bucket.
     */
    @Transactional
    public FotoResponse enviar(MultipartFile arquivo, UUID igrejaId) {
        byte[] conteudo = lerBytes(arquivo);
        ProcessadorImagem.ImagemProcessada imagem = processadorImagem.validarEProcessar(conteudo);

        // Chave aleatória, nunca derivada do nome enviado (evita path traversal e colisão).
        String chave = "fotos/" + igrejaId + "/" + UUID.randomUUID();

        armazenamentoFotos.guardar(chave + "/original", imagem.original(), imagem.tipoOriginal());
        armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.DISPLAY.sufixo(), imagem.display(), "image/jpeg");
        armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.THUMB.sufixo(), imagem.thumb(), "image/jpeg");

        Foto foto = Foto.builder()
                .igreja(Igreja.builder().id(igrejaId).build())
                .chave(chave)
                .tipo(imagem.tipoOriginal())
                .bytes(imagem.original().length)
                .build();
        foto = fotoRepository.save(foto);

        log.info("Foto enviada. foto_id={}, igreja_id={}", foto.getId(), igrejaId);
        return FotoResponse.from(foto);
    }

    /**
     * Lê os bytes de uma versão. Foto de outra igreja retorna 404 (não 403) —
     * "não existe" em vez de "existe, mas não é sua".
     */
    @Transactional(readOnly = true)
    public byte[] ler(UUID id, TamanhoFoto tamanho, UUID igrejaId) {
        Foto foto = fotoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        return armazenamentoFotos.ler(foto.getChave() + "/" + tamanho.sufixo());
    }

    /**
     * Resolve um id de foto para vincula-la a uma entidade (pessoa/evento/igreja).
     * Valida que pertence à igreja do solicitante. Retorna {@code null} quando o id é
     * {@code null} — "sem foto" é uma escolha válida.
     */
    @Transactional(readOnly = true)
    public Foto buscarParaVincular(UUID fotoId, UUID igrejaId) {
        if (fotoId == null) return null;
        return fotoRepository.findByIdAndIgrejaId(fotoId, igrejaId)
                .orElseThrow(() -> new BusinessException("FOTO_INVALIDA",
                        "Foto não encontrada ou não pertence a esta igreja."));
    }

    /**
     * Remove a linha do banco na transação corrente e agenda o apagamento dos arquivos
     * para depois do commit. O bucket não participa de transação: se os bytes sumirem
     * antes do commit e houver rollback, a linha volta mas a foto fica quebrada para sempre.
     * Sem transação ativa (ex.: job de limpeza), apaga tudo na hora.
     */
    @Transactional
    public void remover(UUID id) {
        Foto foto = fotoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        String chave = foto.getChave();

        fotoRepository.delete(foto);
        apagarArquivosAposCommit(chave, id);
    }

    private void apagarArquivosAposCommit(String chave, UUID id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            armazenamentoFotos.remover(chave);
            log.info("Foto removida. foto_id={}", id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                armazenamentoFotos.remover(chave);
                log.info("Foto removida. foto_id={}", id);
            }
        });
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Não foi possível ler o arquivo enviado.");
        }
    }
}
