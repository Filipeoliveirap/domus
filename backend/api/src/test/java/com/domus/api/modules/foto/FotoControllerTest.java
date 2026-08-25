package com.domus.api.modules.foto;

import com.domus.api.shared.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FotoControllerTest {

    FotoService fotoService;
    UsuarioAutenticado usuarioAutenticado;
    CacheFallbackWebP cacheFallbackWebP;
    FotoController controller;

    UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {
        fotoService = mock(FotoService.class);
        usuarioAutenticado = mock(UsuarioAutenticado.class);
        // Cache real (sem mock): é só um Map em memória, mais simples testar de verdade
        // do que mockar o supplier.
        cacheFallbackWebP = new CacheFallbackWebP();
        controller = new FotoController(fotoService, usuarioAutenticado, cacheFallbackWebP);
    }

    @Test
    void respostaVariaPorAcceptParaNaoServirCacheDoFormatoErrado() {
        // Sem "Vary: Accept", um cache/CDN compartilhado na frente (ou até o cache do
        // próprio navegador) pode guardar a resposta só pela URL e servir WebP pra quem
        // pediu JPEG (ou vice-versa) — foi isso que aconteceu testando ao vivo: o <img>
        // (Accept: image/webp nativo) cacheou WebP pra essa URL, e uma chamada seguinte
        // com Accept diferente recebeu o cache errado em vez de reprocessar.
        byte[] webp = webpOpacoDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(webp);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "image/webp");

        assertThat(resposta.getHeaders().getVary()).contains("Accept");
    }

    @Test
    void servePorWebpQuandoClienteDeclaraAceitarNoAccept() {
        byte[] webp = webpOpacoDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(webp);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "image/webp,*/*");

        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/webp");
        assertThat(resposta.getBody()).isEqualTo(webp);
    }

    @Test
    void convertePraJpegQuandoAcceptNaoDeclaraWebpExplicitamente() {
        // "*/*" sozinho (sem "image/webp") não conta como aceitar WebP — é o valor
        // default quando o cliente nem manda o header Accept (ex.: <img> antigo, curl
        // sem -H, download direto).
        byte[] webp = webpOpacoDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(webp);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "*/*");

        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(ehJpegValido(resposta.getBody())).isTrue();
    }

    @Test
    void convertePraJpegQuandoAcceptSoAceitaJpeg() {
        byte[] webp = webpOpacoDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(webp);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "image/jpeg");

        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
    }

    @Test
    void fotoAntigaEmJpegEServidaDiretoIndependenteDoAccept() {
        byte[] jpeg = jpegDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(jpeg);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "*/*");

        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(resposta.getBody()).isEqualTo(jpeg);
    }

    @Test
    void convertePraJpegSemQuebrarQuandoWebpTemTransparencia() {
        // Rede de segurança: mesmo que um WebP com alpha chegue aqui (não deveria, mas se
        // chegar), a conversão não pode lançar exceção.
        byte[] webpComAlpha = webpComAlphaDe(10, 10);
        when(fotoService.lerComFallback(any(), any(), any())).thenReturn(webpComAlpha);

        ResponseEntity<byte[]> resposta = controller.ler(id, TamanhoFoto.THUMB, "image/jpeg");

        assertThat(resposta.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(ehJpegValido(resposta.getBody())).isTrue();
    }

    private byte[] webpComAlphaDe(int largura, int altura) {
        try {
            BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType("Lossy");
                param.setCompressionQuality(0.85f);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] jpegDe(int largura, int altura) {
        try {
            BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] webpOpacoDe(int largura, int altura) {
        try {
            BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionType("Lossy");
                param.setCompressionQuality(0.85f);
                writer.write(null, new IIOImage(img, null, null), param);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean ehJpegValido(byte[] bytes) {
        try {
            return ImageIO.read(new java.io.ByteArrayInputStream(bytes)) != null;
        } catch (IOException e) {
            return false;
        }
    }
}
