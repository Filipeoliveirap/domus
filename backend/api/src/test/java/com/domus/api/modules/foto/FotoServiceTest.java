package com.domus.api.modules.foto;

import com.domus.api.modules.foto.DTOs.FotoResponse;
import com.domus.api.shared.armazenamento.ArmazenamentoEmMemoria;
import com.domus.api.shared.armazenamento.ArmazenamentoException;
import com.domus.api.shared.armazenamento.ArmazenamentoFotos;
import com.domus.api.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Postgres real (não Mockito): o caminho feliz depende de FotoRepository gravando de verdade; storage é {@link ArmazenamentoEmMemoria} (property de teste). */
@SpringBootTest
class FotoServiceTest implements PostgresTestContainerSupport {

    @Autowired
    FotoService service;

    @Autowired
    ArmazenamentoFotos armazenamentoFotos;

    @Autowired
    EntityManagerFactory emf;

    ArmazenamentoEmMemoria armazenamento;

    UUID igrejaId;

    @BeforeEach
    void setup() {
        armazenamento = (ArmazenamentoEmMemoria) armazenamentoFotos;
        igrejaId = UUID.randomUUID();
        executarCommitado(em -> em.createNativeQuery("""
                INSERT INTO igreja (id, nome, email) VALUES (:id, 'Igreja Teste Foto', :email)
                """)
                .setParameter("id", igrejaId)
                .setParameter("email", "foto-teste-" + igrejaId + "@teste.local")
                .executeUpdate());
    }

    @AfterEach
    void limpar() {
        executarCommitado(em -> {
            em.createNativeQuery("DELETE FROM foto WHERE igreja_id = :ig")
                    .setParameter("ig", igrejaId).executeUpdate();
            em.createNativeQuery("DELETE FROM igreja WHERE id = :ig")
                    .setParameter("ig", igrejaId).executeUpdate();
        });
    }

    @Test
    void enviarGuardaAsTresVersoes() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        assertThat(resposta.id()).isNotNull();
        // original + display + thumb
        assertThat(armazenamento.contar(prefixoDaIgreja())).isEqualTo(3);
    }

    @Test
    void lerFotoDeOutraIgrejaDaNaoEncontrado() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        assertThatThrownBy(() -> service.ler(resposta.id(), TamanhoFoto.THUMB, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removerApagaTodasAsVersoesDoBucket() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);
        assertThat(armazenamento.contar(prefixoDaIgreja())).isEqualTo(3);

        service.remover(resposta.id());

        assertThat(armazenamento.contar(prefixoDaIgreja())).isZero();
    }

    @Test
    void lerRetornaOsBytesDaVersaoPedida() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        byte[] bytes = service.ler(resposta.id(), TamanhoFoto.THUMB, igrejaId);

        assertThat(bytes).isNotEmpty();
    }

    @Test
    void lerComFallbackPropagaFalhaRealDeInfraEmVezDeDisfarcarDe404() {
        var resposta = service.enviar(arquivoJpeg(), igrejaId);

        armazenamento.simularFalhaDeInfra(true);
        try {
            assertThatThrownBy(() -> service.lerComFallback(resposta.id(), TamanhoFoto.THUMB, igrejaId))
                    .as("falha de rede/autenticação no storage não pode virar 'foto não encontrada'")
                    .isInstanceOf(ArmazenamentoException.class)
                    .isNotInstanceOf(ResourceNotFoundException.class);
        } finally {
            armazenamento.simularFalhaDeInfra(false);
        }
    }

    /** Chave no bucket é `fotos/{igrejaId}/{UUID-aleatório}`, diferente do id da linha em `foto` — o prefixo por igreja basta pra contar as três versões. */
    private String prefixoDaIgreja() {
        return "fotos/" + igrejaId + "/";
    }

    private MockMultipartFile arquivoJpeg() {
        try {
            BufferedImage imagem = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(imagem, "jpg", out);
            return new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void executarCommitado(Consumer<jakarta.persistence.EntityManager> acao) {
        var em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            acao.accept(em);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }
}
