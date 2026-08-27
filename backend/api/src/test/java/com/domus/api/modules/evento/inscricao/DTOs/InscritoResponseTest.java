package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InscritoResponseTest {

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(UUID.randomUUID());
        return i;
    }

    private InscricaoEvento inscricaoBase(Igreja igreja) {
        return InscricaoEvento.builder()
                .id(UUID.randomUUID())
                .igreja(igreja)
                .evento(Evento.builder().id(UUID.randomUUID()).igreja(igreja).titulo("Retiro").build())
                .status(StatusInscricao.CONFIRMADA)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void mostraNomeConvidadoQuandoPessoaNulaENomeConvidadoPreenchido() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("Maria de Fora");
        i.setTelefoneConvidado("11999998888");

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.nome()).isEqualTo("Maria de Fora");
        assertThat(resp.pessoaRemovida()).isFalse();
        assertThat(resp.telefoneConvidado()).isEqualTo("11999998888");
    }

    @Test
    void mostraCompareceuDaInscricao() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("Maria de Fora");
        i.setCompareceu(true);

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.compareceu()).isTrue();
    }

    @Test
    void mostraPessoaRemovidaQuandoPessoaENomeConvidadoAmbosNulos() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        // pessoa e nomeConvidado ambos nulos — LGPD purgou a pessoa.

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.nome()).isEqualTo("Pessoa removida do sistema");
        assertThat(resp.pessoaRemovida()).isTrue();
    }

    @Test
    void mostraPessoaEConvidadoPorQuandoAmbosPreenchidos() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("João Visitante");

        Pessoa convidante = Pessoa.builder().id(UUID.randomUUID()).nome("Ana Convidante").build();

        InscritoResponse resp = InscritoResponse.from(i, null, null, convidante);

        assertThat(resp.nome()).isEqualTo("João Visitante");
        assertThat(resp.convidadoPorNome()).isEqualTo("Ana Convidante");
    }

    @Test
    void mostraNomeDaPessoaQuandoPessoaPreenchida() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).nome("Carlos Membro").igreja(igreja).build();

        InscritoResponse resp = InscritoResponse.from(i, pessoa, null, null);

        assertThat(resp.nome()).isEqualTo("Carlos Membro");
        assertThat(resp.pessoaRemovida()).isFalse();
        assertThat(resp.convidadoPorNome()).isNull();
    }

    @Test
    void mostraTelefoneEEmailDaPessoaQuandoPessoaPreenchidaESemContatoDeConvidado() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        Pessoa pessoa = Pessoa.builder()
                .id(UUID.randomUUID()).nome("Carlos Membro").igreja(igreja)
                .telefone("11988887777").email("carlos@example.com")
                .build();

        InscritoResponse resp = InscritoResponse.from(i, pessoa, null, null);

        assertThat(resp.telefonePessoa()).isEqualTo("11988887777");
        assertThat(resp.emailPessoa()).isEqualTo("carlos@example.com");
        assertThat(resp.telefoneConvidado()).isNull();
    }

    @Test
    void naoMostraTelefoneNemEmailDePessoaQuandoConvidadoSemCadastro() {
        Igreja igreja = igreja();
        InscricaoEvento i = inscricaoBase(igreja);
        i.setNomeConvidado("Maria de Fora");
        i.setTelefoneConvidado("11999998888");

        InscritoResponse resp = InscritoResponse.from(i, null, null, null);

        assertThat(resp.telefonePessoa()).isNull();
        assertThat(resp.emailPessoa()).isNull();
    }
}
