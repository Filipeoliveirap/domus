package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.ChangePasswordDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.LoginAttemptService;
import com.domus.api.shared.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    AuthenticationManager authenticationManager;
    com.domus.api.config.TokenService tokenService;
    RefreshTokenService refreshTokenService;
    LoginAttemptService loginAttemptService;
    UsuarioRepository usuarioRepository;
    PasswordEncoder passwordEncoder;
    AuthService service;

    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenService = mock(com.domus.api.config.TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(authenticationManager, tokenService, refreshTokenService,
                loginAttemptService, usuarioRepository, passwordEncoder);
    }

    private Usuario usuarioComSenha(String hash) {
        Usuario u = new Usuario();
        u.setId(usuarioId);
        u.setSenhaHash(hash);
        return u;
    }

    @Test
    void alterarSenha_senhaAtualErrada_lancaErro() {
        Usuario usuario = usuarioComSenha("hash-antigo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        var data = new ChangePasswordDTO("errada", "novaSenha123");

        assertThatThrownBy(() -> service.alterarSenha(usuarioId, "token-x", data))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("atual");
    }

    @Test
    void alterarSenha_contaSoGoogle_lancaContaSemSenha() {
        Usuario usuario = usuarioComSenha(null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        var data = new ChangePasswordDTO("qualquer", "novaSenha123");

        assertThatThrownBy(() -> service.alterarSenha(usuarioId, "token-x", data))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) e).getCodigo()).isEqualTo("CONTA_SEM_SENHA"));
    }

    @Test
    void alterarSenha_sucesso_atualizaHashERevogaOutrasSessoes() {
        Usuario usuario = usuarioComSenha("hash-antigo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correta", "hash-antigo")).thenReturn(true);
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var data = new ChangePasswordDTO("correta", "novaSenha123");
        service.alterarSenha(usuarioId, "token-atual", data);

        verify(usuarioRepository).save(argThat(u -> "hash-novo".equals(u.getSenhaHash())));
        verify(refreshTokenService).revogarTodasSessoesExceto(usuarioId, "token-atual");
    }
}
