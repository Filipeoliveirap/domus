package com.domus.api.shared.armazenamento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o TESTE usa armazenamento em memória, não o R2 real.
 *
 * <p>Existe por um erro achado na revisão: a seleção usava {@code @Profile("test")}, mas o
 * projeto nunca ativa esse perfil — então o bean ativo durante os testes era o R2, que
 * tentaria rede e credencial de verdade. Sem este teste, quem mexer na configuração de novo
 * quebraria isso sem perceber.
 */
@SpringBootTest
class ArmazenamentoSelecaoTest {

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
