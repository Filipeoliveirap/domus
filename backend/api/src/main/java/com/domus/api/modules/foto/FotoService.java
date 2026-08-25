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

/** Envio e leitura de fotos; toda foto gera original (nunca servido), display (1200px) e thumb (200px). */
@Service
@Slf4j
@RequiredArgsConstructor
public class FotoService {

    private final FotoRepository fotoRepository;
    private final ArmazenamentoFotos armazenamentoFotos;
    private final ProcessadorImagem processadorImagem;

    /** Grava no bucket antes do banco: se o storage falhar não sobra linha órfã. */
    @Transactional
    public FotoResponse enviar(MultipartFile arquivo, UUID igrejaId) {
        byte[] conteudo = lerBytes(arquivo);
        ProcessadorImagem.ImagemProcessada imagem = processadorImagem.validarEProcessar(conteudo);

        // Chave aleatória, nunca derivada do nome enviado (evita path traversal e colisão).
        String chave = "fotos/" + igrejaId + "/" + UUID.randomUUID();

        armazenamentoFotos.guardar(chave + "/original", imagem.original(), imagem.tipoOriginal());
        armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.DISPLAY.sufixo(), imagem.display(), "image/webp");
        armazenamentoFotos.guardar(chave + "/" + TamanhoFoto.THUMB.sufixo(), imagem.thumb(), "image/webp");

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

    /** Foto de outra igreja retorna 404 (não 403) — "não existe" em vez de "não é sua". */
    @Transactional(readOnly = true)
    public byte[] ler(UUID id, TamanhoFoto tamanho, UUID igrejaId) {
        Foto foto = fotoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        return armazenamentoFotos.ler(foto.getChave() + "/" + tamanho.sufixo());
    }

    /**
     * Lê a foto tentando .webp primeiro (novo), cai pra .jpg (fotos antigas pré-mudança).
     * Retorna null se nenhum dos dois existir.
     */
    @Transactional(readOnly = true)
    public byte[] lerComFallback(UUID id, TamanhoFoto tamanho, UUID igrejaId) {
        Foto foto = fotoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));

        String chaveWebp = foto.getChave() + "/" + tamanho.sufixo();
        String chaveJpg = foto.getChave() + "/" + tamanho.name().toLowerCase() + ".jpg";

        try {
            return armazenamentoFotos.ler(chaveWebp);
        } catch (Exception e) {
            log.debug("WebP não encontrado, tentando JPEG: {}", chaveWebp);
            try {
                return armazenamentoFotos.ler(chaveJpg);
            } catch (Exception e2) {
                throw new ResourceNotFoundException("Foto não encontrada.");
            }
        }
    }

    /** Retorna {@code null} quando o id é {@code null} — "sem foto" é uma escolha válida. */
    @Transactional(readOnly = true)
    public Foto buscarParaVincular(UUID fotoId, UUID igrejaId) {
        if (fotoId == null) return null;
        return fotoRepository.findByIdAndIgrejaId(fotoId, igrejaId)
                .orElseThrow(() -> new BusinessException("FOTO_INVALIDA",
                        "Foto não encontrada ou não pertence a esta igreja."));
    }

    /** Apagamento dos arquivos é agendado pra depois do commit — bucket não participa da transação. */
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
