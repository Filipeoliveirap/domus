package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InscricaoServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    MembroRepository membroRepository;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID membroId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        membroRepository = mock(MembroRepository.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, membroRepository);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(Integer vagas) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(10))
                .vagas(vagas)
                .build();
    }

    private Membro membro(boolean batizado, StatusMembro status) {
        return Membro.builder()
                .id(membroId).igreja(igreja()).nome("Maria")
                .status(status).batizado(batizado)
                .build();
    }

    private void dado(Evento e, Membro m, long ocupadas) {
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(m));
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void inscreveQuandoHaVaga() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 3);

        service.inscrever(eventoId, membroId, null, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void recusaQuandoVagasEsgotadas() {
        dado(evento(5), membro(true, StatusMembro.ATIVO), 5);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void vagasNulasSignificamSemLimite() {
        dado(evento(null), membro(true, StatusMembro.ATIVO), 9999);

        service.inscrever(eventoId, membroId, null, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void eventoExclusivoDeBatizadosRecusaNaoBatizado() {
        Evento e = evento(10);
        e.setExclusivoBatizados(true);
        dado(e, membro(false, StatusMembro.ATIVO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("batizados");
    }

    @Test
    void eventoExclusivoDeMembrosRecusaVisitante() {
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        dado(e, membro(true, StatusMembro.VISITANTE), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recusaEventoJaEncerrado() {
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusDays(1));
        dado(e, membro(true, StatusMembro.ATIVO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
    }

    @Test
    void recusaInscricaoDuplicada() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 0);
        InscricaoEvento existente = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrit");
    }

    @Test
    void reinscricaoReaproveitaLinhaCancelada() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 0);
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.of(cancelada));

        service.inscrever(eventoId, membroId, null, igrejaId);

        assertThat(cancelada.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(cancelada);
    }

    @Test
    void acompanhanteOcupaVaga() {
        Evento e = evento(2);
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e).membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(minha.getId(), igrejaId))
                .thenReturn(Optional.of(minha));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(2L);

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", null), usuarioId, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void quemInscreveuNaoPodeDesinscrever() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .inscritoPorUsuarioId(usuarioId)      // fui EU quem inscrevi
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(outra.getId(), igrejaId))
                .thenReturn(Optional.of(outra));

        // sou MEMBRO, o membro da inscrição não sou eu
        assertThatThrownBy(() -> service.cancelar(
                outra.getId(), usuarioId, UUID.randomUUID(), "MEMBRO", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode cancelar");
    }

    @Test
    void oProprioInscritoPodeCancelar() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(minha.getId(), igrejaId))
                .thenReturn(Optional.of(minha));

        service.cancelar(minha.getId(), usuarioId, membroId, "MEMBRO", igrejaId);

        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void adminPodeCancelarInscricaoDeQualquerUm() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(outra.getId(), igrejaId))
                .thenReturn(Optional.of(outra));

        service.cancelar(outra.getId(), usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        assertThat(outra.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }
}
