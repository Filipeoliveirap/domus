package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Insere linhas "decoy" (canceladas, outro evento, outra igreja) pra provar que a contagem as exclui, não só que bate no caso feliz. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InscricaoRepositoryPresencaTest implements PostgresTestContainerSupport {

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    PessoaRepository pessoaRepository;

    @Autowired
    IgrejaRepository igrejaRepository;

    @Test
    void contaPessoasEConvidadosInscritosEQuemDeFatoCompareceu() {
        Igreja igreja = salvarIgreja();
        Evento evento = salvarEvento(igreja, "Evento Alvo");
        Evento outroEvento = salvarEvento(igreja, "Outro Evento");

        Pessoa pessoaCompareceu = salvarPessoa(igreja, "Compareceu");
        Pessoa pessoaFaltou = salvarPessoa(igreja, "Faltou");
        Pessoa pessoaCancelada = salvarPessoa(igreja, "Cancelada");
        Pessoa pessoaOutroEvento = salvarPessoa(igreja, "Outro Evento Pessoa");

        // inscrição confirmada, compareceu = true, com 2 convidados (1 compareceu, 1 não)
        // — desde a Task 1, cada convidado é sua própria InscricaoEvento (pessoa=null,
        // convidadoPor=quem convidou), não mais uma AcompanhanteInscricao à parte.
        InscricaoEvento inscricaoCompareceu = salvarInscricao(igreja, evento, pessoaCompareceu,
                StatusInscricao.CONFIRMADA, true);
        salvarConvidado(igreja, evento, pessoaCompareceu, "Convidado Compareceu", true);
        salvarConvidado(igreja, evento, pessoaCompareceu, "Convidado Faltou", false);

        // inscrição confirmada, mas não compareceu
        salvarInscricao(igreja, evento, pessoaFaltou, StatusInscricao.CONFIRMADA, false);

        // decoy 1: cancelada no mesmo evento — não deve contar em nada. O convidado dela é
        // cancelado EXPLICITAMENTE também: desde 2026-08-26 cada convidado é sua própria
        // InscricaoEvento e cancelar o titular NÃO cancela em cascata quem ele convidou
        // (decisão de produto, ver InscricaoService#cancelarInterno) — sem cancelar os dois,
        // o convidado continuaria CONFIRMADA e contaria de verdade, não seria um decoy válido.
        InscricaoEvento inscricaoCancelada = salvarInscricao(igreja, evento, pessoaCancelada,
                StatusInscricao.CANCELADA, true);
        salvarConvidadoCancelado(igreja, evento, pessoaCancelada, "Convidado Cancelado");

        // decoy 2: confirmada, mas em OUTRO evento — não deve contar no evento alvo
        salvarInscricao(igreja, outroEvento, pessoaOutroEvento, StatusInscricao.CONFIRMADA, true);

        // -- inscritos (confirmados, independente de presença) --
        // countPessoasInscritas conta só titulares (convidadoPor IS NULL); countConvidadosInscritos
        // conta só convidados (convidadoPor IS NOT NULL) — grupos disjuntos, a soma dá o total real
        // sem duplicar (bug corrigido: antes da correção, countPessoasInscritas incluía os
        // convidados de novo, porque sem filtro ela conta toda InscricaoEvento confirmada).
        assertThat(inscricaoRepository.countPessoasInscritas(evento.getId())).isEqualTo(2);
        assertThat(inscricaoRepository.countConvidadosInscritos(evento.getId())).isEqualTo(2);
        assertThat(inscricaoRepository.countPessoasInscritas(evento.getId())
                + inscricaoRepository.countConvidadosInscritos(evento.getId()))
                .isEqualTo(4); // pessoaCompareceu + pessoaFaltou + 2 convidados de pessoaCompareceu

        // -- compareceram de fato --
        assertThat(inscricaoRepository.countPessoasCompareceram(evento.getId())).isEqualTo(1);
        assertThat(inscricaoRepository.countConvidadosCompareceram(evento.getId())).isEqualTo(1);
        assertThat(inscricaoRepository.countPessoasCompareceram(evento.getId())
                + inscricaoRepository.countConvidadosCompareceram(evento.getId()))
                .isEqualTo(2); // pessoaCompareceu + 1 dos 2 convidados dela

        // -- outro evento não é afetado pelas inscrições do evento alvo --
        assertThat(inscricaoRepository.countPessoasInscritas(outroEvento.getId())).isEqualTo(1);
        assertThat(inscricaoRepository.countPessoasCompareceram(outroEvento.getId())).isEqualTo(1);
    }

    @Test
    void contarParticipantesUnicosContaPessoaUmaSoVezEIgnoraConvidadosEOutraIgreja() {
        Igreja igreja = salvarIgreja();
        Igreja outraIgreja = salvarIgreja();

        Evento evento1 = salvarEvento(igreja, "Evento 1");
        Evento evento2 = salvarEvento(igreja, "Evento 2");
        Evento eventoNaoIncluido = salvarEvento(igreja, "Evento Nao Incluido");
        Evento eventoOutraIgreja = salvarEvento(outraIgreja, "Evento Outra Igreja");

        Pessoa pessoaRepetida = salvarPessoa(igreja, "Repetida");
        Pessoa pessoaSoUmEvento = salvarPessoa(igreja, "So Um Evento");
        Pessoa pessoaNaoCompareceu = salvarPessoa(igreja, "Nao Compareceu");
        Pessoa pessoaOutraIgreja = salvarPessoa(outraIgreja, "Pessoa Outra Igreja");

        // mesma pessoa compareceu nos dois eventos incluídos -> conta 1 vez, não 2
        salvarInscricao(igreja, evento1, pessoaRepetida, StatusInscricao.CONFIRMADA, true);
        salvarInscricao(igreja, evento2, pessoaRepetida, StatusInscricao.CONFIRMADA, true);

        salvarInscricao(igreja, evento1, pessoaSoUmEvento, StatusInscricao.CONFIRMADA, true);

        // confirmada mas não compareceu -> não conta
        salvarInscricao(igreja, evento1, pessoaNaoCompareceu, StatusInscricao.CONFIRMADA, false);

        // decoy: evento que não está na lista consultada -> não deve contar
        Pessoa pessoaEventoForaDaLista = salvarPessoa(igreja, "Evento Fora Da Lista");
        salvarInscricao(igreja, eventoNaoIncluido, pessoaEventoForaDaLista,
                StatusInscricao.CONFIRMADA, true);

        // decoy: outra igreja -> não deve contar mesmo que o evento estivesse (por engano) na lista
        salvarInscricao(outraIgreja, eventoOutraIgreja, pessoaOutraIgreja,
                StatusInscricao.CONFIRMADA, true);

        long total = inscricaoRepository.contarParticipantesUnicos(
                List.of(evento1.getId(), evento2.getId()));

        assertThat(total).isEqualTo(2); // pessoaRepetida (1x) + pessoaSoUmEvento
    }

    private Igreja salvarIgreja() {
        return igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste T5 " + UUID.randomUUID())
                .emailContato("t5-" + UUID.randomUUID() + "@teste.com")
                .build());
    }

    private Evento salvarEvento(Igreja igreja, String titulo) {
        return eventoRepository.save(Evento.builder()
                .igreja(igreja)
                .titulo(titulo + " " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build());
    }

    private Pessoa salvarPessoa(Igreja igreja, String nome) {
        return pessoaRepository.save(Pessoa.builder()
                .igreja(igreja)
                .nome(nome)
                .email(nome.toLowerCase().replace(" ", ".") + "." + UUID.randomUUID() + "@teste.com")
                .vinculo(Vinculo.CONGREGANTE)
                .build());
    }

    private InscricaoEvento salvarInscricao(Igreja igreja, Evento evento, Pessoa pessoa,
                                             StatusInscricao status, boolean compareceu) {
        return inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja)
                .evento(evento)
                .pessoa(pessoa)
                .status(status)
                .compareceu(compareceu)
                .build());
    }

    private InscricaoEvento salvarConvidado(Igreja igreja, Evento evento, Pessoa convidadoPor,
                                             String nomeConvidado, boolean compareceu) {
        return inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja)
                .evento(evento)
                .pessoa(null)
                .convidadoPor(convidadoPor)
                .nomeConvidado(nomeConvidado)
                .status(StatusInscricao.CONFIRMADA)
                .compareceu(compareceu)
                .build());
    }

    private InscricaoEvento salvarConvidadoCancelado(Igreja igreja, Evento evento, Pessoa convidadoPor,
                                                       String nomeConvidado) {
        return inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja)
                .evento(evento)
                .pessoa(null)
                .convidadoPor(convidadoPor)
                .nomeConvidado(nomeConvidado)
                .status(StatusInscricao.CANCELADA)
                .compareceu(false)
                .build());
    }
}
