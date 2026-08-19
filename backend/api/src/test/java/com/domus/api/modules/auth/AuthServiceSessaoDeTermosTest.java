package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.termos.TermoAceiteService;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceSessaoDeTermosTest {

    UsuarioRepository usuarioRepository;
    TermoAceiteService termoAceiteService;
    UsuarioCapacidadeRepository capacidadeRepository;
    AuthService authService;
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        termoAceiteService = mock(TermoAceiteService.class);
        capacidadeRepository = mock(UsuarioCapacidadeRepository.class);
        authService = new AuthService(null, null, null, null, usuarioRepository,
                null, capacidadeRepository, termoAceiteService);
    }

    @Test
    void sessaoDeIncluiPrecisaAceitarTermosDoService() {
        when(usuarioRepository.findSessaoById(usuarioId)).thenReturn(Optional.of(
                new SessaoDTO(usuarioId, "Fulano", "ADMIN_IGREJA",
                        UUID.randomUUID(), "Igreja X", null, null, null, null)));
        when(capacidadeRepository.findByUsuarioId(usuarioId)).thenReturn(List.of());
        when(termoAceiteService.precisaAceitar(usuarioId)).thenReturn(true);
        LocalDateTime ultimo = LocalDateTime.now();
        when(termoAceiteService.dataUltimoAceite(usuarioId)).thenReturn(ultimo);

        SessaoDTO sessao = authService.sessaoDe(usuarioId);

        assertThat(sessao.precisaAceitarTermos()).isTrue();
        assertThat(sessao.termosAceitosEm()).isEqualTo(ultimo);
    }

    @Test
    void sessaoDeIndicaFalseQuandoJaAceitouVersaoAtual() {
        when(usuarioRepository.findSessaoById(usuarioId)).thenReturn(Optional.of(
                new SessaoDTO(usuarioId, "Fulano", "ADMIN_IGREJA",
                        UUID.randomUUID(), "Igreja X", null, null, null, null)));
        when(capacidadeRepository.findByUsuarioId(usuarioId)).thenReturn(List.of());
        when(termoAceiteService.precisaAceitar(usuarioId)).thenReturn(false);
        when(termoAceiteService.dataUltimoAceite(usuarioId)).thenReturn(LocalDateTime.now());

        SessaoDTO sessao = authService.sessaoDe(usuarioId);

        assertThat(sessao.precisaAceitarTermos()).isFalse();
    }
}
