package com.domus.api.modules.foto;

import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem();

    private byte[] jpegDe(int largura, int altura) throws IOException {
        BufferedImage img = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private BufferedImage ler(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void geraDisplayEThumbNosTamanhosCertos() throws IOException {
        var r = processador.validarEProcessar(jpegDe(3000, 2000));

        assertThat(ler(r.display()).getWidth()).isEqualTo(1200);
        assertThat(ler(r.thumb()).getWidth()).isEqualTo(200);
    }

    @Test
    void naoAumentaImagemMenorQueOAlvo() throws IOException {
        // Esticar 100px para 1200px so produz borrao e ocupa espaco a toa.
        var r = processador.validarEProcessar(jpegDe(100, 80));

        assertThat(ler(r.display()).getWidth()).isEqualTo(100);
    }

    @Test
    void recusaArquivoQueNaoEImagem() {
        byte[] lixo = "isto nao e uma imagem, e um .jpg mentiroso".getBytes();

        assertThatThrownBy(() -> processador.validarEProcessar(lixo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não é uma imagem");
    }

    @Test
    void recusaImagemAcimaDoLimiteDePixels() throws IOException {
        // Bomba de descompressao: o limite de 10MB do multipart NAO protege contra isto —
        // o perigo nao e o tamanho do arquivo, e o do bitmap depois de decodificado.
        assertThatThrownBy(() -> processador.validarEProcessar(jpegDe(9000, 9000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grande demais");
    }

    @Test
    void aplicaOrientacaoDoExifEDescartaOsMetadados() throws IOException {
        // Fixture deitada (400x200) com Orientation=6: se apagar o EXIF sem aplicar a rotação, fica deitada pra sempre — imagem gerada por código nunca tem EXIF pra pegar isso.
        try (InputStream in = getClass().getResourceAsStream("/fotos/com-exif-girada.jpg")) {
            assertThat(in).as("fixture com EXIF precisa existir").isNotNull();
            var r = processador.validarEProcessar(in.readAllBytes());

            BufferedImage display = ler(r.display());
            assertThat(display.getHeight())
                    .as("depois de aplicada a rotacao a imagem fica EM PE")
                    .isGreaterThan(display.getWidth());

            assertThat(contemMarcadorExif(r.display()))
                    .as("EXIF (e o GPS junto) nao pode sobreviver")
                    .isFalse();
            assertThat(contemMarcadorExif(r.thumb())).isFalse();
        }
    }

    /** APP1 (0xFFE1) e o segmento onde o JPEG guarda EXIF — incluindo a coordenada de GPS. */
    private boolean contemMarcadorExif(byte[] jpeg) {
        for (int i = 0; i < jpeg.length - 1; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xE1) return true;
        }
        return false;
    }
}
