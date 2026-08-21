package com.domus.api.shared.armazenamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Seleção usava @Profile("test"), mas o projeto nunca ativa esse perfil — o bean ativo nos testes era o R2 de verdade. */
@SpringBootTest
class ArmazenamentoSelecaoTest implements PostgresTestContainerSupport {

    @Autowired
    ArmazenamentoFotos armazenamento;

    @Test
    void testeUsaArmazenamentoEmMemoria() {
        assertThat(armazenamento).isInstanceOf(ArmazenamentoEmMemoria.class);
    }

    @Test
    void guardaLeERemovePorPrefixo() {
        armazenamento.guardar("p/a.jpg", new byte[]{1, 2, 3}, "image/jpeg");
        armazenamento.guardar("p/b.jpg", new byte[]{4}, "image/jpeg");

        assertThat(armazenamento.ler("p/a.jpg")).containsExactly(1, 2, 3);

        armazenamento.remover("p/");

        assertThatThrownBy(() -> armazenamento.ler("p/a.jpg"))
                .isInstanceOf(ArmazenamentoException.class);
    }
}
