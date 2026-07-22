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
 * Envio e leitura de fotos. Os bytes vivem no bucket (privado); aqui só o metadado.
 *
 * <p>Toda foto vira TRÊS objetos no bucket, sob o mesmo prefixo aleatório: o original (nunca
 * servido, guardado só como fonte caso um dia precise reprocessar), o display (1200px) e o
 * thumb (200px) — ver {@link ProcessadorImagem} e {@link TamanhoFoto}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FotoService {

    private final FotoRepository fotoRepository;
    private final ArmazenamentoFotos armazenamentoFotos;
    private final ProcessadorImagem processadorImagem;

    /**
     * Guarda as três versões e só então grava a linha em {@code foto}.
     *
     * <p>A ordem importa: se o storage falhar, não sobra linha no banco apontando para um
     * bucket vazio. Se o banco falhar DEPOIS do storage ter gravado, sobra lixo órfão no
     * bucket — mas isso a rotina de limpeza (Task 5, {@code buscarOrfas}) resolve; uma linha
     * órfã no banco não teria como ser limpa da mesma forma.
     */
    @Transactional
    public FotoResponse enviar(MultipartFile arquivo, UUID igrejaId) {
        byte[] conteudo = lerBytes(arquivo);
        ProcessadorImagem.ImagemProcessada imagem = processadorImagem.validarEProcessar(conteudo);

        // Chave ALEATÓRIA — nunca derivada do nome enviado pelo usuário. O nome de arquivo
        // jamais vira caminho no bucket (evita path traversal e colisão proposital).
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
     * Lê os bytes de uma versão. Busca por {@code findByIdAndIgrejaId}: foto de outra igreja
     * dá o MESMO 404 de foto inexistente — nunca 403, que já entregaria "existe, mas não é
     * sua".
     */
    @Transactional(readOnly = true)
    public byte[] ler(UUID id, TamanhoFoto tamanho, UUID igrejaId) {
        Foto foto = fotoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        return armazenamentoFotos.ler(foto.getChave() + "/" + tamanho.sufixo());
    }

    /**
     * Resolve o id de foto enviado por um formulário (pessoa/evento/igreja), validando que
     * ela pertence à igreja de quem está salvando. Nunca vincula um FK "no escuro": se o id
     * não existir ou for de outra igreja, falha antes de gravar qualquer referência.
     *
     * @return {@code null} quando {@code fotoId} é {@code null} — "sem foto" é uma escolha
     *         válida, não um erro.
     */
    @Transactional(readOnly = true)
    public Foto buscarParaVincular(UUID fotoId, UUID igrejaId) {
        if (fotoId == null) return null;
        return fotoRepository.findByIdAndIgrejaId(fotoId, igrejaId)
                .orElseThrow(() -> new BusinessException("FOTO_INVALIDA",
                        "Foto não encontrada ou não pertence a esta igreja."));
    }

    /** Apaga as três versões do bucket e a linha em {@code foto}. */
    @Transactional
    /**
     * Remove a foto: a linha DENTRO da transação, os arquivos só DEPOIS do commit.
     *
     * <p><b>Por que separado:</b> o bucket não participa de transação. Apagando os arquivos
     * junto com a linha, um erro posterior na mesma transação (outbox, reindexação, qualquer
     * regra que rode depois) faria rollback — a linha voltaria, a pessoa continuaria
     * apontando para ela, e os BYTES já teriam sumido. A foto existiria no banco e daria 404
     * para sempre.
     *
     * <p>Adiando o apagamento para depois do commit: se a transação der certo, o arquivo
     * some; se der errado, o rollback restaura a linha e o arquivo nunca foi tocado.
     *
     * <p>Sem transação ativa (o job de limpeza roda assim), apaga na hora — não há rollback
     * a temer.
     */
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
