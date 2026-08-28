package com.domus.api.modules.evento.inscricao;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V33 já rodou (Flyway aplica todas as migrations antes do teste) — este teste só
 * confirma que a tabela/colunas antigas sumiram e que a estrutura nova existe. A
 * conversão de dados em si (linhas reais) não tem como testar aqui porque o banco de
 * teste nasce vazio — quem prova que o SQL de conversão funciona é rodar a migration
 * contra um dump real antes de aplicar em produção (ensaio manual, mesmo padrão do
 * backup do Postgres).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AcompanhanteMigracaoV33Test implements PostgresTestContainerSupport {

    @Autowired EntityManager entityManager;

    @Test
    void tabelaAcompanhanteInscricaoNaoExisteMais() {
        var resultado = entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'acompanhante_inscricao')")
            .getSingleResult();
        assertThat((Boolean) resultado).isFalse();
    }

    @Test
    void colunaAcompanhanteIdSumiuDeCobrancaECampoPersonalizado() {
        var cobranca = (Boolean) entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'cobranca_evento' AND column_name = 'acompanhante_id')")
            .getSingleResult();
        var resposta = (Boolean) entityManager.createNativeQuery(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'resposta_campo_personalizado' AND column_name = 'acompanhante_id')")
            .getSingleResult();
        assertThat(cobranca).isFalse();
        assertThat(resposta).isFalse();
    }
}
