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
 * Valida e transforma a imagem enviada. É aqui que mora o risco desta feature.
 *
 * <p>Três perigos, na ordem em que aparecem:
 *
 * <ol>
 *   <li><b>Arquivo que não é imagem.</b> Validar pela extensão é confiar em quem envia —
 *       um {@code .jpg} pode conter qualquer coisa. Validamos tentando LER o conteúdo.</li>
 *
 *   <li><b>Bomba de descompressão.</b> Um PNG de 5 MB pode virar gigabytes de bitmap. O
 *       limite do multipart NÃO protege contra isso: o perigo não é o tamanho do arquivo,
 *       é o da imagem depois de decodificada. Por isso a dimensão é lida do CABEÇALHO,
 *       antes de qualquer pixel ser decodificado.</li>
 *
 *   <li><b>EXIF.</b> Guarda a orientação e, pior, a <b>coordenada de GPS</b> de onde a foto
 *       foi tirada — uma foto de perfil tirada em casa entrega o endereço de quem o sistema
 *       esconde o endereço com tanto cuidado. Descartar é obrigatório. E aplicar a rotação
 *       ANTES de descartar também: sem isso, toda foto de celular tirada em pé fica deitada
 *       para sempre.</li>
 * </ol>
 */
@Component
public class ProcessadorImagem {

    /**
     * WebP ficou de fora: o {@code ImageIO} do Java 21 não lê sem dependência extra, e o
     * seletor de arquivo — de celular ou de desktop — entrega JPEG ou PNG em praticamente
     * todos os casos. A SAÍDA é sempre JPEG.
     */
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

    /** Lê o tipo do CONTEÚDO. A extensão do arquivo não participa da decisão. */
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

    /**
     * Confere a dimensão SEM decodificar os pixels — é o que impede a bomba de descompressão
     * de estourar a memória antes de qualquer checagem acontecer.
     */
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
                // Já é menor que o alvo: regrava no mesmo tamanho (o que ainda descarta o
                // EXIF e aplica a rotação) em vez de ampliar. Esticar só borra e ocupa espaço.
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

    /**
     * Cabe dentro da caixa do alvo?
     *
     * <p>Usa a dimensão do cabeçalho — de novo sem decodificar — e considera a orientação:
     * numa foto que será girada, largura e altura trocam de papel.
     */
    private boolean cabeDentroDe(byte[] entrada, int lado) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(entrada))) {
            ImageReader leitor = ImageIO.getImageReaders(in).next();
            leitor.setInput(in);
            int maiorLado = Math.max(leitor.getWidth(0), leitor.getHeight(0));
            leitor.dispose();
            return maiorLado <= lado;
        } catch (IOException e) {
            // Se não deu para ler a dimensão aqui, deixa o redimensionamento normal decidir —
            // o pior caso é uma ampliação, não um erro.
            return false;
        }
    }
}
