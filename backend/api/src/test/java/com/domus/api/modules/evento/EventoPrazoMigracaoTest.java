package com.domus.api.modules.evento;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoPrazoMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void v38_cria_colunas_de_prazo_no_evento() {
        var colunas = jdbc.queryForList(
            "SELECT column_name, is_nullable, column_default FROM information_schema.columns " +
            "WHERE table_name = 'evento' AND column_name IN " +
            "('inscricoes_ate','aviso_prazo_proximo_em','aviso_prazo_fechado_em')");
        assertThat(colunas).hasSize(3);
    }

    @Test
    void v39_troca_boolean_por_enum_de_politica() {
        var colunas = jdbc.queryForList(
            "SELECT is_nullable, data_type, character_maximum_length, column_default " +
            "FROM information_schema.columns WHERE table_name = 'evento' " +
            "AND column_name = 'politica_cancelamento_apos_prazo'");
        assertThat(colunas).hasSize(1);
        var col = colunas.get(0);
        assertThat(col.get("is_nullable")).isEqualTo("NO");
        assertThat(col.get("data_type")).isEqualTo("character varying");
        assertThat(col.get("character_maximum_length")).isEqualTo(30);
        assertThat(String.valueOf(col.get("column_default"))).contains("PERMITIDO_COM_REEMBOLSO");

        var boolCount = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = 'evento' " +
            "AND column_name = 'permite_cancelar_apos_prazo'", Integer.class);
        assertThat(boolCount).isEqualTo(0);
    }

    @Test
    void v38_cria_coluna_de_carimbo_na_inscricao() {
        var count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name = 'inscricao_evento' AND column_name = 'aviso_prazo_incompleto_em'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
