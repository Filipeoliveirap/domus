package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.LoginAttemptService;
import com.domus.api.shared.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceContaSemSenhaTest {

    AuthenticationManager authenticationManager;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    LoginAttemptService loginAttemptService;
    UsuarioRepository usuarioRepository;
    UsuarioCapacidadeRepository capacidadeRepository;
    PasswordEncoder passwordEncoder;
    AuthService service;

    @BeforeEach
    void setup() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        capacidadeRepository = mock(UsuarioCapacidadeRepository.class);
        service = new AuthService(authenticationManager, tokenService, refreshTokenService,
                loginAttemptService, usuarioRepository, passwordEncoder, capacidadeRepository);
    }

    @Test
    void login_contaSoGoogle_lancaContaSemSenha() {
        when(loginAttemptService.estaBloqueado("g@g.com")).thenReturn(false);
        Usuario u = Usuario.builder().senhaHash(null).ativo(true).build();
        when(usuarioRepository.findByEmail("g@g.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new AuthenticationDTO("g@g.com", "qualquer")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Google");

        verifyNoInteractions(authenticationManager);
    }
}
