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
 *
 * <p>Pós-revisão: os testes de {@link InscricaoService#inscreverPessoas} foram acrescentados
 * porque era exatamente ali — não em {@link InscricaoService#inscrever} direto — que um
 * admin conseguia contornar FAIXA_ETARIA para SI MESMO (ver
 * {@code auto_inscricao_via_inscreverPessoas_com_o_proprio_pessoaId_e_recusada}).
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

    /** Idêntico ao {@link #dado}, mas usado pelo caminho de {@code inscreverPessoas}. */
    private void dadoParaInscreverPessoas(Evento e, Pessoa pessoa, long ocupadas) {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(pessoa.getId(), igrejaId)).thenReturn(Optional.of(pessoa));
        when(inscricaoRepository.listarPessoaIdsJaInscritos(eventoId, List.of(pessoa.getId())))
                .thenReturn(List.of());
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoa.getId())).thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void auto_inscricao_de_gestor_fora_da_faixa_e_permitida_com_confirmado() {
        // Decisão do autor (2026-07-23): quem gerencia pode se inscrever num recorte fora do
        // seu — organiza o evento e pode participar (equipe do retiro de jovens). Exige o
        // clique de confirmação (confirmado=true), então não é burla casual.
        Pessoa admin40 = pessoaComIdade(40);
        dado(eventoJovens(), admin40, 0);

        service.inscrever(eventoId, admin40.getId(), null, admin40.getId(),
                "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void auto_inscricao_de_gestor_sem_confirmado_e_recusada() {
        // Sem o clique explícito, a restrição vale mesmo para o gestor.
        Pessoa admin40 = pessoaComIdade(40);
        dado(eventoJovens(), admin40, 0);

        assertThatThrownBy(() -> service.inscrever(
                eventoId, admin40.getId(), null, admin40.getId(), "ADMIN_IGREJA", false, igrejaId))
                .isInstanceOf(NaoElegivelException.class);

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void auto_inscricao_de_nao_gestor_fora_da_faixa_e_recusada_mesmo_com_confirmado() {
        // A proteção que importa: o membro comum NUNCA contorna a própria restrição, nem
        // mandando confirmado=true — podeGerenciar=false barra.
        Pessoa comum40 = pessoaComIdade(40);
        dado(eventoJovens(), comum40, 0);

        assertThatThrownBy(() -> service.inscrever(
                eventoId, comum40.getId(), null, comum40.getId(), "ACESSO_COMUM", true, igrejaId))
                .isInstanceOf(NaoElegivelException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void admin_inscreve_terceiro_fora_da_faixa_com_confirmado() {
        Pessoa lider34 = pessoaComIdade(34);
        UUID minhaPessoaId = UUID.randomUUID(); // pessoa do admin, DIFERENTE do alvo
        dado(eventoJovens(), lider34, 0);

        service.inscrever(eventoId, lider34.getId(), adminUsuarioId, minhaPessoaId,
                "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void confirmado_de_quem_nao_gerencia_e_ignorado() {
        Pessoa lider34 = pessoaComIdade(34);
        UUID minhaPessoaId = UUID.randomUUID();
        dado(eventoJovens(), lider34, 0);

        // ACESSO_COMUM não gerencia inscrições: confirmado=true é ignorado, não aceito.
        assertThatThrownBy(() -> service.inscrever(
                eventoId, lider34.getId(), adminUsuarioId, minhaPessoaId, "ACESSO_COMUM", true, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void confirmado_nao_derruba_vagas_esgotadas() {
        // Vaga que não existe não vira exceção administrativa.
        Pessoa pessoaQualquer = pessoaComIdade(30);
        UUID minhaPessoaId = UUID.randomUUID();
        dado(eventoLotado(), pessoaQualquer, 1); // 1 vaga, 1 já ocupada -> esgotado

        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaQualquer.getId(), adminUsuarioId, minhaPessoaId,
                "ADMIN_IGREJA", true, igrejaId))
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
                eventoId, pessoaDe34.getId(), null, pessoaDe34.getId(), "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");
    }

    // ---------------------------------------------------------------------------------
    // inscreverPessoas — Achado 1 (CRITICAL) da revisão pós-Task-4.
    //
    // O bug: autoInscricao era derivado só de "inscritoPorOuNull == null". Em
    // inscreverPessoas, inscritoPorUsuarioId é SEMPRE o id de usuário de quem chama (nunca
    // null), então autoInscricao dava sempre false — mesmo quando o admin colocava o PRÓPRIO
    // pessoaId na lista. Resultado: admin inscrevia A SI MESMO com confirmado=true e
    // contornava FAIXA_ETARIA, que "auto-inscrição nunca contorna" deveria proibir.
    // ---------------------------------------------------------------------------------

    @Test
    void gestor_pode_se_inscrever_via_inscreverPessoas_com_o_proprio_pessoaId_e_confirmado() {
        // Antes bloqueado (a trava contra burla, quando auto-inscrição nunca contornava).
        // Com a decisão de 2026-07-23, o gestor pode se inscrever num recorte fora do seu por
        // qualquer caminho, desde que confirme. A proteção real permanece no papel: um
        // ACESSO_COMUM continua barrado (acesso_comum_com_confirmado_via_inscreverPessoas).
        Pessoa admin40 = pessoaComIdade(40);
        dadoParaInscreverPessoas(eventoJovens(), admin40, 0);

        service.inscreverPessoas(eventoId, List.of(admin40.getId()), adminUsuarioId,
                admin40.getId(), "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void admin_inscreve_outra_pessoa_fora_da_faixa_via_inscreverPessoas_com_confirmado() {
        Pessoa lider34 = pessoaComIdade(34);
        UUID minhaPessoaId = UUID.randomUUID(); // pessoa do admin, DIFERENTE do alvo
        dadoParaInscreverPessoas(eventoJovens(), lider34, 0);

        service.inscreverPessoas(eventoId, List.of(lider34.getId()), adminUsuarioId,
                minhaPessoaId, "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void acesso_comum_com_confirmado_via_inscreverPessoas_e_recusado() {
        Pessoa lider34 = pessoaComIdade(34);
        UUID minhaPessoaId = UUID.randomUUID();
        dadoParaInscreverPessoas(eventoJovens(), lider34, 0);

        assertThatThrownBy(() -> service.inscreverPessoas(
                eventoId, List.of(lider34.getId()), UUID.randomUUID(), minhaPessoaId,
                "ACESSO_COMUM", true, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");

        verify(inscricaoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------------------
    // Achado 3 (IMPORTANT): o 422 não pode vazar nome/idade de terceiro para quem não
    // gerencia inscrições.
    // ---------------------------------------------------------------------------------

    @Test
    void gestor_ve_mensagem_detalhada_com_nome_e_idade_no_422() {
        // Gestor na PRIMEIRA tentativa (sem confirmar ainda): recebe o 422 com o detalhe
        // (nome + idade) para poder decidir se inscreve mesmo assim. Confirmando, passaria —
        // auto_inscricao_de_gestor_fora_da_faixa_e_permitida_com_confirmado cobre esse lado.
        Pessoa admin40 = pessoaComIdade(40);
        dado(eventoJovens(), admin40, 0);

        assertThatThrownBy(() -> service.inscrever(
                eventoId, admin40.getId(), null, admin40.getId(), "ADMIN_IGREJA", false, igrejaId))
                .isInstanceOf(NaoElegivelException.class)
                .hasMessageContaining("Fulano")
                .hasMessageContaining("40 anos");
    }

    @Test
    void nao_gestor_recebe_mensagem_generica_sem_nome_nem_idade_no_422() {
        Pessoa pessoaDe34 = pessoaComIdade(34);
        dado(eventoJovens(), pessoaDe34, 0);

        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaDe34.getId(), null, pessoaDe34.getId(), "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(NaoElegivelException.class)
                .hasMessageNotContaining("Fulano")
                .hasMessageNotContaining("34 anos")
                .hasMessageContaining("não atende aos requisitos");
    }
}
