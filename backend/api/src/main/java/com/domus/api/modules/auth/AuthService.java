package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ContaBloqueadaException;
import com.domus.api.shared.security.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginResponseDTO login(AuthenticationDTO data) {
        log.info("Tentativa de login. email={}", data.email());

        if (loginAttemptService.estaBloqueado(data.email())) {
            long minutos = loginAttemptService.minutosRestantes(data.email());
            log.warn("Tentativa de login em conta bloqueada. email={}", data.email());
            throw new ContaBloqueadaException(minutos);
        }

        try {
            var authToken = new UsernamePasswordAuthenticationToken(data.email(), data.senha());
            var auth = authenticationManager.authenticate(authToken);

            var usuario = (Usuario) auth.getPrincipal();
            var token = tokenService.generateToken(usuario);

            loginAttemptService.registrarSucesso(data.email());

            usuario.registrarLogin();
            usuarioRepository.save(usuario);
            log.info("Login bem-sucedido. email={}, igreja_id={}", usuario.getEmail(), usuario.getIgreja().getId());

            return new LoginResponseDTO(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getRole().getNome(),
                    usuario.getIgreja().getId(),
                    usuario.getIgreja().getNome(),
                    token
            );

        } catch (BadCredentialsException | InternalAuthenticationServiceException e) {
            Usuario arquivado = usuarioRepository.findByEmailIncluindoArquivados(data.email())
                    .filter(u -> u.getDeleteAt() != null)
                    .filter(u -> passwordEncoder.matches(data.senha(), u.getSenhaHash()))
                    .orElse(null);

            if (arquivado != null) {
                throw new BusinessException("CONTA_ARQUIVADA",
                        "Esta conta foi arquivada. Entre em contato com um administrador.");
            }

            throw new BusinessException("CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos.");
        }
    }
}
