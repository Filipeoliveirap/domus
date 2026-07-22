package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.NaoElegivelException;
import com.domus.api.modules.evento.elegibilidade.regras.RegraEstadoCivil;
import com.domus.api.modules.evento.elegibilidade.regras.RegraFaixaEtaria;
import com.domus.api.modules.evento.elegibilidade.regras.RegraSexo;
import com.domus.api.modules.evento.elegibilidade.regras.RegraVinculo;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Task 4: a Task 3 entregou o MECANISMO de elegibilidade; este teste prova que ele foi
 * CONECTADO ao fluxo de inscrição de verdade — as quatro regras que não podem ser erradas.
 */
class InscricaoElegibilidadeTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    PessoaRepository membroRepository;
    UsuarioRepository usuarioRepository;
    ElegibilidadeService elegibilidadeService;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID adminUsuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        membroRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        // Real, não mock: o que este teste prova é justamente que o InscricaoService chama
        // a avaliação DE VERDADE, não um duplo que sempre aprova.
        elegibilidadeService = new ElegibilidadeService(List.of(
                new RegraFaixaEtaria(), new RegraVinculo(),
                new RegraEstadoCivil(), new RegraSexo()));
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, membroRepository, usuarioRepository, elegibilidadeService);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    /** Evento "de jovens": só quem tem de 18 a 29 anos. */
    private Evento eventoJovens() {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro de Jovens").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).idadeMin(18).idadeMax(29)
                .build();
    }

    private Evento eventoLotado() {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).vagas(1)
                .build();
    }

    private Pessoa pessoaComIdade(int idade) {
        return Pessoa.builder()
                .id(UUID.randomUUID()).igreja(igreja()).nome("Fulano")
                .dataNascimento(LocalDate.now().minusYears(idade))
                .vinculo(Vinculo.MEMBRO)
                .build();
    }

    private void dado(Evento e, Pessoa pessoa, long ocupadas) {
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(pessoa.getId(), igrejaId)).thenReturn(Optional.of(pessoa));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoa.getId())).thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void auto_inscricao_fora_da_faixa_e_recusada_mesmo_para_admin() {
        // A exceção existe para inscrever TERCEIROS. Deixar o admin burlar a própria
        // inscrição transformaria a restrição em decoração para quem tem acesso.
        Pessoa admin40 = pessoaComIdade(40);
        dado(eventoJovens(), admin40, 0);

        // auto-inscrição: inscritoPorOuNull == null, mesmo passando role/confirmado de admin.
        assertThatThrownBy(() -> service.inscrever(
                eventoId, admin40.getId(), null, "ADMIN_IGREJA", true, igrejaId))
                .isInstanceOf(NaoElegivelException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void admin_inscreve_terceiro_fora_da_faixa_com_confirmado() {
        Pessoa lider34 = pessoaComIdade(34);
        dado(eventoJovens(), lider34, 0);

        service.inscrever(eventoId, lider34.getId(), adminUsuarioId, "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void confirmado_de_quem_nao_gerencia_e_ignorado() {
        Pessoa lider34 = pessoaComIdade(34);
        dado(eventoJovens(), lider34, 0);

        // ACESSO_COMUM não gerencia inscrições: confirmado=true é ignorado, não aceito.
        assertThatThrownBy(() -> service.inscrever(
                eventoId, lider34.getId(), adminUsuarioId, "ACESSO_COMUM", true, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void confirmado_nao_derruba_vagas_esgotadas() {
        // Vaga que não existe não vira exceção administrativa.
        Pessoa pessoaQualquer = pessoaComIdade(30);
        dado(eventoLotado(), pessoaQualquer, 1); // 1 vaga, 1 já ocupada -> esgotado

        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaQualquer.getId(), adminUsuarioId, "ADMIN_IGREJA", true, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "VAGAS_ESGOTADAS");
    }

    @Test
    void get_elegibilidade_e_post_concordam_sobre_a_mesma_pessoa() {
        Evento evento = eventoJovens();
        Pessoa pessoaDe34 = pessoaComIdade(34);

        // Mesma checagem que alimentaria o GET /eventos/{id}/elegibilidade.
        Elegibilidade previa = elegibilidadeService.avaliar(evento, pessoaDe34);
        assertThat(previa.apto()).isFalse();

        dado(evento, pessoaDe34, 0);
        // POST direto (auto-inscrição, sem confirmar nada) tem que concordar com o GET.
        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaDe34.getId(), null, "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");
    }
}
