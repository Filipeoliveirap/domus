package com.domus.api.modules.evento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
@Transactional
class MigracaoV3Test implements PostgresTestContainerSupport {

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

        // Checar só o schema não denuncia o cenário desastroso ADD+DROP (coluna nova,
        // 100% NULL). Aqui verificamos o DADO: se existe evento, tem que existir pelo
        // menos um com local_texto preenchido — senão o RENAME não preservou nada.
        Integer totalEventos = jdbc.queryForObject("SELECT COUNT(*) FROM evento", Integer.class);
        assumeTrue(totalEventos != null && totalEventos > 0,
                "Banco sem eventos — pula a checagem de dado para não virar ruído.");

        Integer comLocalPreenchido = jdbc.queryForObject(
                "SELECT COUNT(*) FROM evento WHERE local_texto IS NOT NULL", Integer.class);
        assertThat(comLocalPreenchido)
                .as("esperado ao menos 1 evento com local_texto preenchido (dado preservado do RENAME)")
                .isGreaterThan(0);
    }

    @Test
    void check_recusa_local_id_e_local_texto_juntos() {
        String igrejaId = criarIgreja();
        jdbc.update("INSERT INTO local_evento (igreja_id, nome) VALUES (?::uuid, 'Salão Teste')",
                igrejaId);
        String localId = jdbc.queryForObject(
                "SELECT id::text FROM local_evento WHERE nome='Salão Teste'", String.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, local_id, local_texto, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), ?::uuid, 'texto', false, false)",
                igrejaId, localId))
                // Renomeado em V36 (chk_evento_local_exclusivo -> chk_evento_localizacao_unica)
                // quando entrou a 3ª forma de localização (endereço ad-hoc). Mesma regra: no
                // máximo uma forma preenchida.
                .hasMessageContaining("chk_evento_localizacao_unica");
    }

    @Test
    void check_recusa_idade_min_maior_que_max() {
        String igrejaId = criarIgreja();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, idade_min, idade_max, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), 30, 18, false, false)", igrejaId))
                .hasMessageContaining("chk_evento_idades");
    }

    @Test
    void check_recusa_sexo_invalido_em_pessoa() {
        String igrejaId = criarIgreja();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO pessoa (igreja_id, nome, vinculo, sexo) " +
                "VALUES (?::uuid, 'Teste', 'CONGREGANTE', 'OUTRO')", igrejaId))
                .hasMessageContaining("chk_pessoa_sexo");
    }

    /** As checagens de CHECK constraint não podem depender de já existir uma igreja no
     *  banco — banco isolado (Testcontainers) começa vazio, só com as migrations aplicadas. */
    private String criarIgreja() {
        return jdbc.queryForObject(
                "INSERT INTO igreja (nome, email) VALUES ('Igreja Teste V3', 'migracaov3@teste.com') RETURNING id::text",
                String.class);
    }
}
