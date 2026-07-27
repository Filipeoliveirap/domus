package com.domus.api.modules.foto;

import com.domus.api.shared.exception.BusinessException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Valida e transforma a imagem enviada. Três riscos são mitigados aqui:
 * <ol>
 *   <li>Arquivo que não é imagem: valida pelo conteúdo, nunca pela extensão.</li>
 *   <li>Bomba de descompressão: lê a dimensão do cabeçalho antes de decodificar pixels,
 *       evitando que um PNG de 5 MB vire gigabytes de bitmap.</li>
 *   <li>EXIF: descarta metadados (incluindo coordenada de GPS) e aplica a orientação
 *       antes de regravar para que fotos de celular não saiam deitadas.</li>
 * </ol>
 * <p>WebP não é aceito — o ImageIO do Java 21 não lê sem dependência extra, e celular/desktop
 * entrega JPEG ou PNG na prática. A saída é sempre JPEG.
 */
@Component
public class ProcessadorImagem {

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png");

    /** ~50 megapixels. Acima disso o bitmap descomprimido passa de 200 MB. */
    private static final long MAX_PIXELS = 50_000_000L;

    private static final double QUALIDADE_JPEG = 0.85;

    public record ImagemProcessada(byte[] original, byte[] display, byte[] thumb, String tipoOriginal) {}

    public ImagemProcessada validarEProcessar(byte[] entrada) {
        String tipo = detectarTipo(entrada);
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new BusinessException("FORMATO_NAO_ACEITO", "Envie uma imagem JPEG ou PNG.");
        }

        validarDimensoes(entrada);

        return new ImagemProcessada(
                entrada,
                redimensionar(entrada, TamanhoFoto.DISPLAY),
                redimensionar(entrada, TamanhoFoto.THUMB),
                tipo);
    }

    /** Lê o tipo do conteúdo. A extensão do arquivo não participa da decisão. */
    private String detectarTipo(byte[] entrada) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            if (!leitores.hasNext()) {
                throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
            }
            String formato = leitores.next().getFormatName().toLowerCase();
            return switch (formato) {
                case "jpeg", "jpg" -> "image/jpeg";
                case "png" -> "image/png";
                default -> "image/" + formato;
            };
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
        }
    }

    /** Lê a dimensão do cabeçalho sem decodificar pixels — é o que impede a bomba de descompressão. */
    private void validarDimensoes(byte[] entrada) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(in);
            ImageReader leitor = leitores.next();
            leitor.setInput(in);
            long pixels = (long) leitor.getWidth(0) * leitor.getHeight(0);
            leitor.dispose();

            if (pixels > MAX_PIXELS) {
                throw new BusinessException("IMAGEM_GRANDE_DEMAIS",
                        "Esta imagem é grande demais. Envie uma com no máximo 50 megapixels.");
            }
        } catch (IOException e) {
            throw new BusinessException("ARQUIVO_INVALIDO", "Este arquivo não é uma imagem.");
        }
    }

    private byte[] redimensionar(byte[] entrada, TamanhoFoto tamanho) {
        try {
            int lado = tamanho.getLadoMaximo();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Thumbnails.Builder<?> builder = Thumbnails.of(new ByteArrayInputStream(entrada))
                    // Aplica a rotação do EXIF antes de regravar. Sem isto a foto sai deitada.
                    .useExifOrientation(true)
                    .outputFormat("jpg")
                    .outputQuality(QUALIDADE_JPEG);

            if (cabeDentroDe(entrada, lado)) {
                // Já cabe no alvo: regrava no mesmo tamanho para descartar EXIF, sem ampliar.
                builder.scale(1.0);
            } else {
                builder.size(lado, lado).keepAspectRatio(true);
            }

            builder.toOutputStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("FALHA_PROCESSAMENTO",
                    "Não foi possível processar esta imagem. Tente outra.");
        }
    }

    /** Cabe dentro da caixa do alvo? Usa a dimensão do cabeçalho sem decodificar. */
    private boolean cabeDentroDe(byte[] entrada, int lado) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            ImageReader leitor = ImageIO.getImageReaders(in).next();
            leitor.setInput(in);
            int maiorLado = Math.max(leitor.getWidth(0), leitor.getHeight(0));
            leitor.dispose();
            return maiorLado <= lado;
        } catch (IOException e) {
            return false;
        }
    }
}
