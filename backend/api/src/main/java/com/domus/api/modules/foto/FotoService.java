package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.armazenamento.ArmazenamentoFotos;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    /** Apaga as três versões do bucket e a linha em {@code foto}. */
    @Transactional
    public void remover(UUID id) {
        Foto foto = fotoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Foto não encontrada."));
        armazenamentoFotos.remover(foto.getChave());
        fotoRepository.delete(foto);
        log.info("Foto removida. foto_id={}", id);
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Não foi possível ler o arquivo enviado.");
        }
    }
}
