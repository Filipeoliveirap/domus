package com.domus.api.shared.security;

import com.domus.api.shared.exception.SessaoExpiradaException;
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
    private static final String PREFIXO_USUARIO_FAMILIAS = "usuariofamilias:";
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

    public record ResultadoRotacao(String novoToken, UUID usuarioId) {}

    public String criar(UUID usuarioId) {
        String familyId = UUID.randomUUID().toString();
        String token = gerarTokenOpaco();
        redisTemplate.opsForValue().set(chaveToken(token), usuarioId + SEPARADOR + familyId, ttl);
        redisTemplate.opsForValue().set(chaveFamilia(familyId), token, ttl);
        // Indexa a família sob o usuário, para permitir revogar todas as sessões dele de uma vez.
        String chaveIndice = chaveUsuarioFamilias(usuarioId);
        redisTemplate.opsForSet().add(chaveIndice, familyId);
        redisTemplate.expire(chaveIndice, ttl);
        log.debug("Refresh token criado (nova família). usuario_id={}", usuarioId);
        return token;
    }

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
            throw new SessaoExpiradaException("SESSAO_REVOGADA",
                    "Detectamos um uso suspeito da sua sessão. Faça login novamente.");
        }

        String novoToken = gerarTokenOpaco();
        redisTemplate.opsForValue().set(chaveToken(novoToken), usuarioId + SEPARADOR + familyId, ttl);
        redisTemplate.opsForValue().set(chaveFamilia(familyId), novoToken, ttl);
        log.debug("Refresh token rotacionado. usuario_id={}", usuarioId);
        return new ResultadoRotacao(novoToken, usuarioId);
    }

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

    /**
     * Revoga TODAS as sessões (famílias) de um usuário de uma vez. Usado na troca de
     * senha: derruba qualquer sessão ativa, inclusive a de um eventual invasor.
     */
    public void revogarTodasSessoes(UUID usuarioId) {
        String chaveIndice = chaveUsuarioFamilias(usuarioId);
        var familias = redisTemplate.opsForSet().members(chaveIndice);
        if (familias != null) {
            for (String familyId : familias) {
                revogarFamilia(familyId);
            }
        }
        redisTemplate.delete(chaveIndice);
        log.info("Todas as sessões revogadas. usuario_id={}", usuarioId);
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

    private String chaveUsuarioFamilias(UUID usuarioId) {
        return PREFIXO_USUARIO_FAMILIAS + usuarioId;
    }
}
