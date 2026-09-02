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
class EventoEnderecoAdHocMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void colunasDeEnderecoAdHocExistem() {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'evento'
              AND column_name IN ('cep','logradouro','numero','complemento','bairro','cidade','uf')
            """, Integer.class);
        assertThat(n).isEqualTo(7);
    }

    @Test
    void checkRecusaDuasFormasDeLocalizacao() {
        jdbc.update("""
            INSERT INTO igreja (id, nome, email)
            VALUES ('a1111111-1111-1111-1111-111111111111', 'Igreja Migracao', 'mig@teste.com')
            """);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_texto, cidade)
            VALUES (gen_random_uuid(), 'a1111111-1111-1111-1111-111111111111'::uuid, 'Ev', now(), 'Chácara', 'Recife')
            """))
            .hasMessageContaining("chk_evento_localizacao_unica");
    }
}
