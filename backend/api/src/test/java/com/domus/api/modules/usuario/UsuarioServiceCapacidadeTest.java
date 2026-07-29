package com.domus.api.modules.usuario;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.auth.PasswordResetService;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UsuarioServiceCapacidadeTest {

    UsuarioRepository usuarioRepository;
    IgrejaRepository igrejaRepository;
    RoleRepository roleRepository;
    PessoaRepository membroRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    PasswordResetService passwordResetService;
    EmailService emailService;
    EventoRepository eventoRepository;
    UsuarioCapacidadeRepository capacidadeRepository;
    UsuarioService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID concedidoPorId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        roleRepository = mock(RoleRepository.class);
        membroRepository = mock(PessoaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        passwordResetService = mock(PasswordResetService.class);
        emailService = mock(EmailService.class);
        eventoRepository = mock(EventoRepository.class);
        capacidadeRepository = mock(UsuarioCapacidadeRepository.class);
        service = new UsuarioService(usuarioRepository, igrejaRepository, roleRepository,
                membroRepository, cacheEvictor, outboxRegistrador, passwordResetService,
                emailService, eventoRepository, capacidadeRepository);
    }

    private Usuario usuario() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        Usuario u = new Usuario();
        u.setId(usuarioId);
        u.setIgreja(igreja);
        return u;
    }

    @Test
    void concederCapacidadeSalvaQuandoAindaNaoTem() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario()));
        when(capacidadeRepository.existsByUsuarioIdAndCapacidade(usuarioId, "SECRETARIO")).thenReturn(false);

        service.concederCapacidade(usuarioId, "SECRETARIO", igrejaId, concedidoPorId);

        verify(capacidadeRepository).save(any(UsuarioCapacidade.class));
    }

    @Test
    void concederCapacidadeJaExistenteEhIdempotente() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario()));
        when(capacidadeRepository.existsByUsuarioIdAndCapacidade(usuarioId, "TESOUREIRO")).thenReturn(true);

        service.concederCapacidade(usuarioId, "TESOUREIRO", igrejaId, concedidoPorId);

        verify(capacidadeRepository, never()).save(any());
    }

    @Test
    void concederCapacidadeDeUsuarioDeOutraIgrejaEh404() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.concederCapacidade(usuarioId, "SECRETARIO", igrejaId, concedidoPorId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concederCapacidadeInvalidaLancaErroDeNegocio() {
        assertThatThrownBy(() -> service.concederCapacidade(usuarioId, "SUPER_ADMIN", igrejaId, concedidoPorId))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(capacidadeRepository);
    }

    @Test
    void revogarCapacidadeRemoveALinha() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario()));

        service.revogarCapacidade(usuarioId, "SECRETARIO", igrejaId);

        verify(capacidadeRepository).deleteByUsuarioIdAndCapacidade(usuarioId, "SECRETARIO");
    }

    @Test
    void revogarCapacidadeQueNaoTemEhIdempotente() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario()));

        service.revogarCapacidade(usuarioId, "TESOUREIRO", igrejaId);

        verify(capacidadeRepository).deleteByUsuarioIdAndCapacidade(usuarioId, "TESOUREIRO");
    }

    @Test
    void revogarCapacidadeDeUsuarioDeOutraIgrejaEh404() {
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revogarCapacidade(usuarioId, "SECRETARIO", igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void revogarCapacidadeInvalidaLancaErroDeNegocio() {
        assertThatThrownBy(() -> service.revogarCapacidade(usuarioId, "SUPER_ADMIN", igrejaId))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(capacidadeRepository);
    }
}
