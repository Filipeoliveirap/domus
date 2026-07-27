package com.domus.api.modules.auth;

import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class PasswordResetService {

    private static final String PREFIXO = "pwreset:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;
    private final String frontendUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            StringRedisTemplate redisTemplate,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            RefreshTokenService refreshTokenService,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.redisTemplate = redisTemplate;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
        this.frontendUrl = frontendUrl;
    }

    /** Gera um token de definição de senha (uso único), guarda no Redis com o TTL dado. */
    public String gerarTokenDefinicaoSenha(UUID usuarioId, Duration ttl) {
        String token = gerarToken();
        redisTemplate.opsForValue().set(chave(token), usuarioId.toString(), ttl);
        return token;
    }

    /** Monta o link da tela de definição de senha, com flag {@code convite=1} para o modo convite. */
    public String linkDefinicaoSenha(String token, boolean convite) {
        return frontendUrl + "/reset-password?token=" + token + (convite ? "&convite=1" : "");
    }

    public void solicitar(String email) {
        usuarioRepository.findByEmail(email).ifPresentOrElse(
                usuario -> {
                    String token = gerarTokenDefinicaoSenha(usuario.getId(), TTL);
                    String link = linkDefinicaoSenha(token, false);
                    try {
                        emailService.enviar(email, "Redefinição de senha — Domus",
                                montarCorpo(usuario.getNome(), link));
                        log.info("Solicitação de reset de senha processada. usuario_id={}", usuario.getId());
                    } catch (RuntimeException e) {
                        // Falha de envio não pode virar 500: quebraria a anti-enumeração e a UX.
                        // Registra e segue; a resposta ao cliente é sempre genérica.
                        log.error("Falha ao enviar e-mail de reset. usuario_id={}", usuario.getId(), e);
                    }
                },
                () -> log.info("Solicitação de reset para e-mail sem conta (ignorado). email={}", email)
        );
    }

    /** Efetiva a troca de senha a partir do token e revoga todas as sessões do usuário. */
    public void redefinir(String token, String novaSenha) {
        String usuarioId = redisTemplate.opsForValue().get(chave(token));
        if (usuarioId == null) {
            throw new BusinessException("TOKEN_INVALIDO",
                    "Link de redefinição inválido ou expirado. Solicite um novo.");
        }

        Usuario usuario = usuarioRepository.findById(UUID.fromString(usuarioId))
                .orElseThrow(() -> new BusinessException("TOKEN_INVALIDO",
                        "Link de redefinição inválido ou expirado. Solicite um novo."));

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        redisTemplate.delete(chave(token));                 // token é de uso único
        refreshTokenService.revogarTodasSessoes(usuario.getId());
        log.info("Senha redefinida e sessões revogadas. usuario_id={}", usuario.getId());
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String chave(String token) {
        return PREFIXO + token;
    }

    private String montarCorpo(String nome, String link) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2>Redefinição de senha</h2>
                  <p>Olá, %s.</p>
                  <p>Recebemos um pedido para redefinir a senha da sua conta no Domus.
                     Clique no botão abaixo para criar uma nova senha:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px;
                       text-decoration: none; border-radius: 6px;">Redefinir senha</a>
                  </p>
                  <p style="color: #666; font-size: 14px;">O link expira em 30 minutos.
                     Se você não pediu isso, pode ignorar este e-mail com segurança.</p>
                </div>
                """.formatted(nome, link);
    }
}
