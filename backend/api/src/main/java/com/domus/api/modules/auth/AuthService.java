package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.ChangePasswordDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioCapacidade;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.termos.TermoAceiteService;
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
    private final UsuarioCapacidadeRepository capacidadeRepository;
    private final TermoAceiteService termoAceiteService;

    public LoginResponseDTO login(AuthenticationDTO data) {
        log.info("Tentativa de login. email={}", data.email());

        if (loginAttemptService.estaBloqueado(data.email())) {
            long minutos = loginAttemptService.minutosRestantes(data.email());
            log.warn("Tentativa de login em conta bloqueada. email={}", data.email());
            throw new ContaBloqueadaException(minutos);
        }

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

            if (usuario.getIgreja() != null) {
                var igreja = usuario.getIgreja();
                var igrejaEfetiva = (igreja.getIgrejaMae() != null) ? igreja.getIgrejaMae() : igreja;
                boolean ehAdmin = usuario.getRole() != null && "ADMIN_IGREJA".equals(usuario.getRole().getNome());
                boolean ehFilha = igreja.getIgrejaMae() != null;

                if (igrejaEfetiva.getStatusTenant() == com.domus.api.modules.igreja.StatusTenant.SUSPENSO) {
                    log.warn("Tentativa de login em tenant suspenso. igreja_id={}, ehAdmin={}, ehFilha={}", igreja.getId(), ehAdmin, ehFilha);
                    if (ehAdmin && !ehFilha) {
                        throw new BusinessException("TENANT_SUSPENSO", "Conta suspensa. Entre em contato com o suporte do Domus.");
                    } else if (ehAdmin && ehFilha) {
                        throw new BusinessException("TENANT_SUSPENSO", "A conta da família de igrejas foi suspensa. Entre em contato com a igreja contratante do plano.");
                    } else {
                        throw new BusinessException("TENANT_SUSPENSO", String.format("A conta da igreja %s foi suspensa.", igreja.getNome()));
                    }
                }

                if (igrejaEfetiva.getStatusAssinatura() == com.domus.api.modules.igreja.StatusAssinatura.CANCELADA) {
                    log.warn("Tentativa de login em assinatura cancelada. igreja_id={}, ehAdmin={}, ehFilha={}", igreja.getId(), ehAdmin, ehFilha);
                    if (ehAdmin && !ehFilha) {
                        throw new BusinessException("ASSINATURA_CANCELADA", "A assinatura da sua igreja foi cancelada.");
                    } else if (ehAdmin && ehFilha) {
                        throw new BusinessException("ASSINATURA_CANCELADA", "A conta da família de igrejas foi cancelada. Entre em contato com a igreja contratante do plano.");
                    } else {
                        throw new BusinessException("ASSINATURA_CANCELADA", String.format("A conta da igreja %s foi cancelada.", igreja.getNome()));
                    }
                }
            }

            var token = tokenService.generateToken(usuario);
            var refreshToken = refreshTokenService.criar(usuario.getId());

            loginAttemptService.registrarSucesso(data.email());

            log.info("Login realizado com sucesso. email={}", data.email());

            return new LoginResponseDTO(new TokenPairDTO(token, refreshToken));

        } catch (BadCredentialsException e) {
            loginAttemptService.registrarFalha(data.email());
            log.warn("Credenciais inválidas no login. email={}", data.email());
            throw new BadCredentialsException("E-mail ou senha incorretos.");
        } catch (DisabledException e) {
            log.warn("Tentativa de login com usuário desativado. email={}", data.email());
            throw new DisabledException("Sua conta está desativada. Entre em contato com a administração.");
        }
    }

    public void logout(String refreshToken) {
        refreshTokenService.revogar(refreshToken);
        log.info("Logout efetuado (refresh token revogado).");
    }

    public SessaoDTO sessaoDe(UUID usuarioId) {
        SessaoDTO sessao = usuarioRepository.findSessaoById(usuarioId)
                .orElseThrow(() -> {
                    log.warn("Sessão pedida para usuário inexistente. usuario_id={}", usuarioId);
                    return new SessaoExpiradaException("SESSAO_INVALIDA",
                            "Sessão expirada. Faça login novamente.");
                });
        return new SessaoDTO(sessao.id(), sessao.pessoaId(), sessao.nome(), sessao.role(),
                sessao.igrejaId(), sessao.igrejaNome(), sessao.fotoId(),
                sessao.cargo(), sessao.igrejaSigla(), sessao.igrejaLogoId(),
                capacidadeRepository.findByUsuarioId(usuarioId).stream()
                        .map(UsuarioCapacidade::getCapacidade).toList(),
                termoAceiteService.precisaAceitar(usuarioId),
                termoAceiteService.dataUltimoAceite(usuarioId),
                sessao.rotulos());
    }

    public void alterarSenha(UUID usuarioId, String refreshTokenAtual, ChangePasswordDTO data) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new SessaoExpiradaException("SESSAO_INVALIDA",
                        "Sessão expirada. Faça login novamente."));

        if (usuario.getSenhaHash() == null) {
            log.warn("Troca de senha em conta só-Google. usuario_id={}", usuarioId);
            throw new BusinessException("CONTA_SEM_SENHA",
                    "Esta conta usa login com Google e não tem senha para trocar.");
        }

        if (!passwordEncoder.matches(data.senhaAtual(), usuario.getSenhaHash())) {
            log.warn("Troca de senha com senha atual incorreta. usuario_id={}", usuarioId);
            throw new BusinessException("SENHA_ATUAL_INCORRETA", "A senha atual informada está incorreta.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(data.novaSenha()));
        usuarioRepository.save(usuario);

        refreshTokenService.revogarTodasSessoesExceto(usuarioId, refreshTokenAtual);
        log.info("Senha alterada pelo próprio usuário. usuario_id={}", usuarioId);
    }
}
