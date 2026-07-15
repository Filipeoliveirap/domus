package com.domus.api.shared.security;

import com.domus.api.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private static final String PREFIXO_TOKEN = "refresh:";
    private static final String PREFIXO_FAMILIA = "refreshfam:";
    private static final String SEPARADOR = "|";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${security.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMillis(refreshExpirationMs);
    }

    /** Resultado de uma rotação bem-sucedida: o novo refresh token e de quem ele é. */
    public record ResultadoRotacao(String novoToken, UUID usuarioId) {}

    /**
     * Cria uma nova FAMÍLIA de refresh tokens para o usuário (usado no login e no cadastro).
     * Uma família representa uma sessão: todos os tokens que nascem de rotações sucessivas
     * do login pertencem a ela, e são revogados juntos.
     */
    public String criar(UUID usuarioId) {
        String familyId = UUID.randomUUID().toString();
        String token = gerarTokenOpaco();
        redisTemplate.opsForValue().set(chaveToken(token), usuarioId + SEPARADOR + familyId, ttl);
        redisTemplate.opsForValue().set(chaveFamilia(familyId), token, ttl);
        log.debug("Refresh token criado (nova família). usuario_id={}", usuarioId);
        return token;
    }

    /**
     * Consome o token apresentado e emite um novo na mesma família (rotação).
     * <ul>
     *   <li>Token desconhecido/expirado, ou família já revogada → devolve null.</li>
     *   <li>Token conhecido mas que NÃO é o atual da família (reuso = sinal de roubo) →
     *       revoga a família inteira e lança exceção.</li>
     *   <li>Token atual → rotaciona e devolve o novo token.</li>
     * </ul>
     */
    public ResultadoRotacao rotacionar(String tokenApresentado) {
        if (tokenApresentado == null || tokenApresentado.isBlank()) return null;

        String valor = redisTemplate.opsForValue().get(chaveToken(tokenApresentado));
        if (valor == null) return null;

        String[] partes = valor.split("\\" + SEPARADOR);
        UUID usuarioId = UUID.fromString(partes[0]);
        String familyId = partes[1];

        String tokenAtual = redisTemplate.opsForValue().get(chaveFamilia(familyId));
        if (tokenAtual == null) return null;

        if (!tokenApresentado.equals(tokenAtual)) {
            revogarFamilia(familyId);
            log.warn("Reuso de refresh token detectado — família revogada. usuario_id={}, family_id={}",
                    usuarioId, familyId);
            throw new BusinessException("SESSAO_REVOGADA",
                    "Detectamos um uso suspeito da sua sessão. Faça login novamente.");
        }

        String novoToken = gerarTokenOpaco();
        redisTemplate.opsForValue().set(chaveToken(novoToken), usuarioId + SEPARADOR + familyId, ttl);
        redisTemplate.opsForValue().set(chaveFamilia(familyId), novoToken, ttl);
        log.debug("Refresh token rotacionado. usuario_id={}", usuarioId);
        return new ResultadoRotacao(novoToken, usuarioId);
    }

    /** Revoga a família inteira a que o token pertence. É o logout de verdade. */
    public void revogar(String token) {
        if (token == null || token.isBlank()) return;
        String valor = redisTemplate.opsForValue().get(chaveToken(token));
        if (valor == null) return;
        String familyId = valor.split("\\" + SEPARADOR)[1];
        revogarFamilia(familyId);
    }

    private void revogarFamilia(String familyId) {
        redisTemplate.delete(chaveFamilia(familyId));
    }

    private String gerarTokenOpaco() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String chaveToken(String token) {
        return PREFIXO_TOKEN + token;
    }

    private String chaveFamilia(String familyId) {
        return PREFIXO_FAMILIA + familyId;
    }
}
