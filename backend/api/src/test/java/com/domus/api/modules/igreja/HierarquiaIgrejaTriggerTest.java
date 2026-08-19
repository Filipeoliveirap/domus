package com.domus.api.modules.igreja;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SQL direto, sem passar por VinculoService de propósito — o trigger existe pro dia em que alguém escrever num caminho que não conhece as regras da aplicação.
 * Postgres real (`replace = NONE`): trigger não existe em H2.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HierarquiaIgrejaTriggerTest {

    @Autowired
    EntityManager em;

    /** Insere uma igreja por SQL nativo e devolve o id, sem tocar no VinculoService. */
    private Object criarIgreja(String nome) {
        Object id = em.createNativeQuery("""
                INSERT INTO igreja (nome, email) VALUES (:nome, :email) RETURNING id
                """)
                .setParameter("nome", nome)
                .setParameter("email", nome.toLowerCase().replace(" ", "") + "@trigger.test")
                .getSingleResult();
        return id;
    }

    private void definirMae(Object filhaId, Object maeId) {
        em.createNativeQuery("UPDATE igreja SET igreja_mae_id = :mae WHERE id = :filha")
                .setParameter("mae", maeId)
                .setParameter("filha", filhaId)
                .executeUpdate();
        em.flush();
    }

    @Test
    void vinculoDeDoisNiveisEhAceito() {
        Object sede = criarIgreja("Trigger Sede A");
        Object filha = criarIgreja("Trigger Filha A");

        assertThatCode(() -> definirMae(filha, sede)).doesNotThrowAnyException();
    }

    @Test
    void ciclo_maeQueViraFilhaDaPropriaFilha_ehBarradoPeloBanco() {
        Object a = criarIgreja("Trigger Ciclo A");
        Object b = criarIgreja("Trigger Ciclo B");
        definirMae(b, a); // B é congregação de A

        // Agora A tenta virar congregação de B. É o estado que faz pertenceAFamilia()
        // aprovar nos dois sentidos e vazar financeiro entre as duas igrejas.
        assertThatThrownBy(() -> definirMae(a, b))
                .hasMessageContaining("2 niveis");
    }

    @Test
    void terceiroNivel_congregacaoDeCongregacao_ehBarradoPeloBanco() {
        Object sede = criarIgreja("Trigger Nivel Sede");
        Object filha = criarIgreja("Trigger Nivel Filha");
        Object neta = criarIgreja("Trigger Nivel Neta");
        definirMae(filha, sede);

        assertThatThrownBy(() -> definirMae(neta, filha))
                .hasMessageContaining("2 niveis");
    }

    @Test
    void autoVinculo_ehBarradoPeloBanco() {
        Object sozinha = criarIgreja("Trigger Auto");

        assertThatThrownBy(() -> definirMae(sozinha, sozinha))
                .hasMessageContaining("mae de si mesma");
    }

    @Test
    void desvincularSempreFunciona() {
        Object sede = criarIgreja("Trigger Desv Sede");
        Object filha = criarIgreja("Trigger Desv Filha");
        definirMae(filha, sede);

        // Voltar a ser independente nunca pode ser barrado — senão a filha ficaria presa.
        assertThatCode(() -> {
            em.createNativeQuery("UPDATE igreja SET igreja_mae_id = NULL WHERE id = :id")
                    .setParameter("id", filha)
                    .executeUpdate();
            em.flush();
        }).doesNotThrowAnyException();

        Object mae = em.createNativeQuery("SELECT igreja_mae_id FROM igreja WHERE id = :id")
                .setParameter("id", filha)
                .getSingleResult();
        assertThat(mae).isNull();
    }
}
