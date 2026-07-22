package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.elegibilidade.regras.RegraEstadoCivil;
import com.domus.api.modules.evento.elegibilidade.regras.RegraFaixaEtaria;
import com.domus.api.modules.evento.elegibilidade.regras.RegraSexo;
import com.domus.api.modules.evento.elegibilidade.regras.RegraVinculo;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.pessoa.Vinculo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElegibilidadeServiceTest {

    private final ElegibilidadeService service = new ElegibilidadeService(List.of(
            new RegraFaixaEtaria(), new RegraVinculo(),
            new RegraEstadoCivil(), new RegraSexo()));

    private Evento eventoSemRestricao() { return Evento.builder().titulo("Culto").build(); }

    private Pessoa pessoaCom(int idade) {
        return Pessoa.builder()
                .nome("Fulano")
                .dataNascimento(LocalDate.now().minusYears(idade))
                .vinculo(Vinculo.MEMBRO)
                .build();
    }

    @Test
    void evento_sem_restricao_aprova_qualquer_pessoa() {
        assertThat(service.avaliar(eventoSemRestricao(), pessoaCom(40)).apto()).isTrue();
    }

    @Test
    void dentro_da_faixa_aprova() {
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        assertThat(service.avaliar(e, pessoaCom(25)).apto()).isTrue();
    }

    @Test
    void fora_da_faixa_reprova_e_e_contornavel() {
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        Elegibilidade r = service.avaliar(e, pessoaCom(34));

        assertThat(r.apto()).isFalse();
        assertThat(r.impedimentos()).hasSize(1);
        assertThat(r.impedimentos().get(0).codigo()).isEqualTo(CodigoImpedimento.FAIXA_ETARIA);
        assertThat(r.impedimentos().get(0).contornavel()).isTrue();
    }

    @Test
    void limites_da_faixa_sao_INCLUSIVOS() {
        // "de 18 até 29" tem que aceitar quem tem 18 e quem tem 29. Errar aqui é um bug
        // silencioso que só aparece no aniversário de alguém.
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        assertThat(service.avaliar(e, pessoaCom(18)).apto()).isTrue();
        assertThat(service.avaliar(e, pessoaCom(29)).apto()).isTrue();
        assertThat(service.avaliar(e, pessoaCom(17)).apto()).isFalse();
        assertThat(service.avaliar(e, pessoaCom(30)).apto()).isFalse();
    }

    @Test
    void sem_data_de_nascimento_reprova_com_CODIGO_PROPRIO() {
        // O código separado é o que permite a tela dizer "procure a secretaria" em vez de
        // "você está fora da faixa" — a causa é um cadastro incompleto, não a idade.
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        Pessoa p = Pessoa.builder().nome("Sem Data").vinculo(Vinculo.MEMBRO).build();

        Elegibilidade r = service.avaliar(e, p);

        assertThat(r.apto()).isFalse();
        assertThat(r.impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_DATA_NASCIMENTO);
    }

    @Test
    void impedimentos_sao_ACUMULADOS_nao_interrompidos_na_primeira_falha() {
        // Parar na primeira faria a pessoa corrigir um problema e descobrir o seguinte.
        Evento e = eventoSemRestricao();
        e.setIdadeMin(18); e.setIdadeMax(29);
        e.setRestricaoSexo(Sexo.MULHER);
        e.setExclusivoMembros(true);

        Pessoa p = Pessoa.builder().nome("Homem 40")
                .dataNascimento(LocalDate.now().minusYears(40))
                .sexo(Sexo.HOMEM).vinculo(Vinculo.CONGREGANTE).build();

        Elegibilidade r = service.avaliar(e, p);

        assertThat(r.impedimentos()).extracting(Impedimento::codigo)
                .containsExactlyInAnyOrder(
                        CodigoImpedimento.FAIXA_ETARIA,
                        CodigoImpedimento.SEXO,
                        CodigoImpedimento.EXCLUSIVO_MEMBROS);
    }

    @Test
    void sem_sexo_cadastrado_reprova_com_codigo_proprio() {
        Evento e = eventoSemRestricao(); e.setRestricaoSexo(Sexo.MULHER);
        Pessoa p = Pessoa.builder().nome("Sem Sexo").vinculo(Vinculo.MEMBRO).build();
        assertThat(service.avaliar(e, p).impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_SEXO);
    }

    @Test
    void sem_estado_civil_cadastrado_reprova_com_codigo_proprio() {
        Evento e = eventoSemRestricao(); e.setRestricaoEstadoCivil(EstadoCivil.CASADO);
        Pessoa p = Pessoa.builder().nome("Sem EC").vinculo(Vinculo.MEMBRO).build();
        assertThat(service.avaliar(e, p).impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_ESTADO_CIVIL);
    }

    @Test
    void restricao_de_vinculo_usa_o_exclusivoMembros_que_ja_existia() {
        Evento e = eventoSemRestricao(); e.setExclusivoMembros(true);
        Pessoa congregante = Pessoa.builder().nome("C").vinculo(Vinculo.CONGREGANTE).build();
        Pessoa membro = Pessoa.builder().nome("M").vinculo(Vinculo.MEMBRO).build();

        assertThat(service.avaliar(e, congregante).apto()).isFalse();
        assertThat(service.avaliar(e, membro).apto()).isTrue();
    }
}
