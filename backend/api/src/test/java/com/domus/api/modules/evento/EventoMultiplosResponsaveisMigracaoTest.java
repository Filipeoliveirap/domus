package com.domus.api.modules.evento;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoMultiplosResponsaveisMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void colunasAntigasDeResponsavelSumiramDaTabelaEvento() {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'evento'
              AND column_name IN ('responsavel_pessoa_id', 'responsavel_texto')
            """, Integer.class);
        assertThat(n).isZero();
    }

    @Test
    void eventoResponsavelAceitaSoNomeTexto() {
        seed();
        int ins = jdbc.update("""
            INSERT INTO evento_responsavel (igreja_id, evento_id, nome_texto)
            VALUES ('b1111111-1111-1111-1111-111111111111'::uuid, 'b2222222-2222-2222-2222-222222222222'::uuid, 'Pessoa removida do sistema')
            """);
        assertThat(ins).isEqualTo(1);
    }

    @Test
    void eventoResponsavelRecusaLinhaSemPessoaESemTexto() {
        seed();
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO evento_responsavel (igreja_id, evento_id)
            VALUES ('b1111111-1111-1111-1111-111111111111'::uuid, 'b2222222-2222-2222-2222-222222222222'::uuid)
            """))
            .hasMessageContaining("chk_evento_responsavel_pessoa_ou_texto");
    }

    private void seed() {
        jdbc.update("INSERT INTO igreja (id, nome, email) VALUES "
                + "('b1111111-1111-1111-1111-111111111111', 'Igreja Resp', 'r@r.com')");
        jdbc.update("""
            INSERT INTO evento (id, igreja_id, titulo, inicio_em)
            VALUES ('b2222222-2222-2222-2222-222222222222', 'b1111111-1111-1111-1111-111111111111'::uuid, 'Ev', now())
            """);
    }
}
