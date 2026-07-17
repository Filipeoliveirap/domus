package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.SessaoExpiradaException;
import com.domus.api.shared.exception.ContaBloqueadaException;
import com.domus.api.shared.security.LoginAttemptService;
import com.domus.api.shared.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
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

        // Conta só-Google (senha_hash == null): login nativo não se aplica. Barra antes de
        // deixar o passwordEncoder tropeçar no null, com mensagem que orienta a pessoa.
        usuarioRepository.findByEmail(data.email())
                .filter(u -> u.getSenhaHash() == null)
                .ifPresent(u -> {
                    log.warn("Login nativo em conta só-Google. email={}", data.email());
                    throw new BusinessException("CONTA_SEM_SENHA",
                            "Esta conta usa login com Google. Entre com Google ou defina uma senha para acessar por e-mail.");
                });

        try {
            var authToken = new UsernamePasswordAuthenticationToken(data.email(), data.senha());
            var auth = authenticationManager.authenticate(authToken);

            var usuario = (Usuario) auth.getPrincipal();
            var token = tokenService.generateToken(usuario);
            var refreshToken = refreshTokenService.criar(usuario.getId());

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
                    token,
                    refreshToken
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

            loginAttemptService.registrarFalha(data.email());
            throw new BusinessException("CREDENCIAIS_INVALIDAS", "E-mail ou senha incorretos.");
        }
    }

    public TokenPairDTO refresh(String refreshToken) {
        // Rotaciona: valida, detecta reuso (lança SESSAO_REVOGADA) e emite o próximo token.
        RefreshTokenService.ResultadoRotacao rotacao = refreshTokenService.rotacionar(refreshToken);
        if (rotacao == null) {
            log.warn("Tentativa de refresh com token inválido ou expirado.");
            throw new SessaoExpiradaException("REFRESH_INVALIDO", "Sessão expirada. Faça login novamente.");
        }

        Usuario usuario = usuarioRepository.findById(rotacao.usuarioId())
                .filter(Usuario::isEnabled)
                .orElse(null);
        if (usuario == null) {
            refreshTokenService.revogar(rotacao.novoToken());
            log.warn("Refresh de usuário inexistente ou desativado. usuario_id={}", rotacao.usuarioId());
            throw new SessaoExpiradaException("REFRESH_INVALIDO", "Sessão expirada. Faça login novamente.");
        }

        String novoAccess = tokenService.generateToken(usuario);
        log.info("Access token renovado via refresh. usuario_id={}", rotacao.usuarioId());
        return new TokenPairDTO(novoAccess, rotacao.novoToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revogar(refreshToken);
        log.info("Logout efetuado (refresh token revogado).");
    }

    /**
     * Dados de sessão de quem está autenticado (usado pelo {@code GET /auth/me}).
     *
     * <p>Consulta pelo id em vez de ler o principal: o principal é uma entidade desanexada
     * (o SecurityFilter roda antes do open-in-view), então seus campos LAZY — como
     * {@code igreja} — explodiriam ao serem lidos aqui. Ver {@code findSessaoById}.
     */
    public SessaoDTO sessaoDe(UUID usuarioId) {
        return usuarioRepository.findSessaoById(usuarioId)
                .orElseThrow(() -> {
                    log.warn("Sessão pedida para usuário inexistente. usuario_id={}", usuarioId);
                    return new SessaoExpiradaException("SESSAO_INVALIDA",
                            "Sessão expirada. Faça login novamente.");
                });
    }
}
