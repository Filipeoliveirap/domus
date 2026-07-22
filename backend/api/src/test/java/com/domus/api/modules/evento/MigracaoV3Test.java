package com.domus.api.modules.evento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class MigracaoV3Test {

    @Autowired JdbcTemplate jdbc;

    @Test
    void local_texto_preserva_o_conteudo_da_antiga_coluna_local() {
        // A coluna foi RENOMEADA, não recriada: se alguém trocar o RENAME por um ADD COLUMN,
        // os eventos existentes perdem o local em silêncio e este teste é o que denuncia.
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='evento' AND column_name='local_texto'", Integer.class);
        assertThat(existe).isEqualTo(1);

        Integer antiga = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='evento' AND column_name='local'", Integer.class);
        assertThat(antiga).isZero();
    }

    @Test
    void check_recusa_local_id_e_local_texto_juntos() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        jdbc.update("INSERT INTO local_evento (igreja_id, nome) VALUES (?::uuid, 'Salão Teste')",
                igrejaId);
        String localId = jdbc.queryForObject(
                "SELECT id::text FROM local_evento WHERE nome='Salão Teste'", String.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, local_id, local_texto, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), ?::uuid, 'texto', false, false)",
                igrejaId, localId))
                .hasMessageContaining("chk_evento_local_exclusivo");
    }

    @Test
    void check_recusa_idade_min_maior_que_max() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, idade_min, idade_max, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), 30, 18, false, false)", igrejaId))
                .hasMessageContaining("chk_evento_idades");
    }

    @Test
    void check_recusa_sexo_invalido_em_pessoa() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO pessoa (igreja_id, nome, vinculo, sexo) " +
                "VALUES (?::uuid, 'Teste', 'CONGREGANTE', 'OUTRO')", igrejaId))
                .hasMessageContaining("chk_pessoa_sexo");
    }
}
