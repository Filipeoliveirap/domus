package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final FotoService fotoService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final CacheFallbackWebP cacheFallbackWebP;

    /** O tenant vem do JWT; não há como pedir a foto de outra igreja. */
    @PostMapping
    public ResponseEntity<FotoResponse> enviar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fotoService.enviar(arquivo, usuarioAutenticado.getIgrejaId()));
    }

    /**
     * Lê a foto com content negotiation via Accept header.
     * Cliente que aceita WebP recebe WebP; senão, recebe JPEG (convertido sob demanda com cache).
     * Compatível com fotos antigas (.jpg no bucket).
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> ler(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "DISPLAY") TamanhoFoto tamanho,
            @RequestHeader(value = "Accept", defaultValue = "*/*") String accept) {

        byte[] bytes = fotoService.lerComFallback(id, tamanho, usuarioAutenticado.getIgrejaId());
        // "*/*" NÃO conta como aceitar WebP — é o valor default quando o cliente nem manda
        // o header Accept (curl sem -H, <img> antigo, download direto). Só serve WebP pra
        // quem declara suporte de verdade; o resto cai no fallback JPEG, mais compatível.
        boolean clienteAceitaWebp = accept.contains("image/webp");
        boolean bytesSaoWebp = ehWebp(bytes);

        byte[] resposta;
        MediaType contentType;

        if (bytesSaoWebp && !clienteAceitaWebp) {
            // Converter WebP → JPEG sob demanda (com cache)
            String chaveCache = id + "-" + tamanho.name();
            resposta = cacheFallbackWebP.obter(chaveCache, () -> converterWebpParaJpeg(bytes));
            contentType = MediaType.IMAGE_JPEG;
        } else if (bytesSaoWebp) {
            resposta = bytes;
            contentType = MediaType.valueOf("image/webp");
        } else {
            // Foto antiga em JPEG — serve direto independente do Accept
            resposta = bytes;
            contentType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                // Sem isso, um cache/CDN compartilhado guarda a resposta só pela URL e pode
                // servir WebP pra quem pediu JPEG (ou vice-versa) — visto ao vivo testando:
                // o <img> do navegador (Accept: image/webp nativo) cacheia a URL, e uma
                // chamada seguinte com Accept diferente pega o cache do formato errado.
                .header("Vary", "Accept")
                .body(resposta);
    }

    private boolean ehWebp(byte[] bytes) {
        // Magic bytes do WebP: "RIFF....WEBP" (offsets 0-3, 8-11)
        if (bytes.length < 12) return false;
        return bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private byte[] converterWebpParaJpeg(byte[] webpBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(webpBytes));
            // Rede de segurança: display/thumb já não deveriam ter alpha (ProcessadorImagem
            // achata transparência no envio), mas JPEG não suporta alpha de jeito nenhum —
            // se algum WebP com alpha chegar aqui mesmo assim, ImageIO.write("jpg", ...)
            // falha em vez de silenciosamente descartar o canal. Compor sobre branco garante
            // que a conversão nunca quebra por causa disso.
            if (img.getColorModel().hasAlpha()) {
                BufferedImage opaca = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g2d = opaca.createGraphics();
                g2d.setColor(java.awt.Color.WHITE);
                g2d.fillRect(0, 0, img.getWidth(), img.getHeight());
                g2d.drawImage(img, 0, 0, null);
                g2d.dispose();
                img = opaca;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Falha ao converter WebP para JPEG", e);
        }
    }
}
