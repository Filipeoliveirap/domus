package com.domus.api.modules.foto;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.domus.api.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Valida por conteúdo (nunca extensão), lê dimensão do cabeçalho antes de decodificar pixels
 * (evita bomba de descompressão) e descarta EXIF/GPS. Saída sempre WebP com qualidade 0.85.
 *
 * Substituiu Thumbnailator porque este não suporta WebP via plugin ImageIO.
 * EXIF rotation é aplicada manualmente via metadata-extractor + AffineTransform.
 */
@Component
public class ProcessadorImagem {

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");

    /** ~50 megapixels. Acima disso o bitmap descomprimido passa de 200 MB. */
    private static final long MAX_PIXELS = 50_000_000L;

    private static final float QUALIDADE_WEBP = 0.85f;

    static {
        // Garante que plugins ImageIO (webp-imageio) sejam descobertos via SPI.
        ImageIO.scanForPlugins();
    }

    public record ImagemProcessada(byte[] original, byte[] display, byte[] thumb, String tipoOriginal) {}

    public ImagemProcessada validarEProcessar(byte[] entrada) {
        String tipo = detectarTipo(entrada);
        if (!TIPOS_ACEITOS.contains(tipo)) {
            throw new BusinessException("FORMATO_NAO_ACEITO", "Envie uma imagem JPEG, PNG ou WebP.");
        }

        validarDimensoes(entrada);

        int rotacaoExif = lerRotacaoExif(entrada);

        return new ImagemProcessada(
                entrada,
                redimensionar(entrada, TamanhoFoto.DISPLAY, rotacaoExif),
                redimensionar(entrada, TamanhoFoto.THUMB, rotacaoExif),
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
                case "webp" -> "image/webp";
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

    /**
     * Lê a orientação do EXIF (tag 0x0112). Retorna o ângulo de rotação em graus (0, 90, 180 ou 270).
     * Retorna 0 se não houver EXIF ou se a leitura falhar.
     */
    private int lerRotacaoExif(byte[] entrada) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(entrada));
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 == null || !ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return 0;
            }
            int orientacao = ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            return switch (orientacao) {
                case 3 -> 180;  // Rotated 180°
                case 6 -> 90;   // Rotated 90° CW
                case 8 -> 270;  // Rotated 90° CCW
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }

    private byte[] redimensionar(byte[] entrada, TamanhoFoto tamanho, int rotacaoExif) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(entrada));
            if (original == null) {
                throw new BusinessException("FALHA_PROCESSAMENTO", "Não foi possível ler a imagem.");
            }

            BufferedImage rotacionado = aplicarRotacao(original, rotacaoExif);

            int ladoAlvo = tamanho.getLadoMaximo();
            BufferedImage redimensionado = redimensionarMantendoProporcao(rotacionado, ladoAlvo);

            return codificarWebp(redimensionado);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("FALHA_PROCESSAMENTO",
                    "Não foi possível processar esta imagem. Tente outra.");
        }
    }

    /**
     * Aplica rotação via AffineTransform. Retorna a imagem original se rotação for 0.
     */
    private BufferedImage aplicarRotacao(BufferedImage img, int graus) {
        if (graus == 0) return img;

        double radians = Math.toRadians(graus);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));

        int novaLargura = (int) Math.round(img.getWidth() * cos + img.getHeight() * sin);
        int novaAltura = (int) Math.round(img.getHeight() * cos + img.getWidth() * sin);

        BufferedImage rotacionada = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = rotacionada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        AffineTransform transform = new AffineTransform();
        transform.translate((novaLargura - img.getWidth()) / 2.0, (novaAltura - img.getHeight()) / 2.0);
        transform.rotate(radians, img.getWidth() / 2.0, img.getHeight() / 2.0);
        g2d.drawImage(img, transform, null);
        g2d.dispose();

        return rotacionada;
    }

    /**
     * Redimensiona mantendo aspect ratio. Se a imagem já cabe no alvo, retorna como está
     * (mas regravada para descartar EXIF).
     */
    private BufferedImage redimensionarMantendoProporcao(BufferedImage img, int ladoAlvo) {
        int larguraOriginal = img.getWidth();
        int alturaOriginal = img.getHeight();
        int maiorLado = Math.max(larguraOriginal, alturaOriginal);

        if (maiorLado <= ladoAlvo) {
            return img;
        }

        double fator = (double) ladoAlvo / maiorLado;
        int novaLargura = (int) Math.round(larguraOriginal * fator);
        int novaAltura = (int) Math.round(alturaOriginal * fator);

        BufferedImage redimensionada = new BufferedImage(novaLargura, novaAltura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = redimensionada.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(img, 0, 0, novaLargura, novaAltura, null);
        g2d.dispose();

        return redimensionada;
    }

    /**
     * Codifica BufferedImage em WebP com qualidade 0.85.
     * Usa ImageWriter registrado pelo plugin webp-imageio.
     */
    private byte[] codificarWebp(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("webp");
        if (!writers.hasNext()) {
            throw new IOException("Nenhum writer WebP disponível. Verifique webp-imageio no classpath.");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("Lossy");
            param.setCompressionQuality(QUALIDADE_WEBP);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}
