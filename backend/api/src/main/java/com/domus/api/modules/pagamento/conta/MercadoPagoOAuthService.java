package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MercadoPagoOAuthService {

    /**
     * Critical 3a (revisão final de branch): TTL curto porque o fluxo (clicar "conectar" →
     * autorizar no Mercado Pago → voltar pro callback) é rápido — mesmo padrão de
     * {@code PasswordResetService} pra token de uso único.
     */
    private static final Duration TTL_STATE = Duration.ofMinutes(10);
    private static final String PREFIXO_STATE = "mpoauth:state:";

    private final ContaPagamentoIgrejaRepository repository;
    private final CredencialEncryptor encryptor;
    private final MercadoPagoOAuthClient client;
    private final StringRedisTemplate redisTemplate;
    private final String clientId;
    private final String redirectUri;
    private final SecureRandom secureRandom = new SecureRandom();

    public MercadoPagoOAuthService(ContaPagamentoIgrejaRepository repository,
                                    CredencialEncryptor encryptor,
                                    MercadoPagoOAuthClient client,
                                    StringRedisTemplate redisTemplate,
                                    @Value("${app.pagamento.mercadopago.client-id}") String clientId,
                                    @Value("${app.pagamento.mercadopago.redirect-uri}") String redirectUri) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.client = client;
        this.redisTemplate = redisTemplate;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
    }

    /**
     * Critical 3a (revisão final de branch): antes desta correção, {@code state} era só o
     * {@code igrejaId} em texto (URL-encoded) — nunca verificado de volta no callback, e de
     * quebra previsível (não era um nonce anti-CSRF de verdade, era só o dado que o
     * callback já lê da sessão). Corrigido pra um nonce aleatório de verdade (mesmo padrão
     * de {@link SecureRandom} usado em {@code CobrancaEventoService.gerarToken}), guardado
     * no Redis associado ao {@code usuarioId} que iniciou o fluxo, com TTL curto — o
     * callback só aceita se o {@code state} devolvido bater com o que foi gerado PARA
     * AQUELE usuário. Sem isso, um atacante podia induzir um admin logado a abrir uma URL
     * de callback com o {@code code} do atacante — o {@code igrejaId} da vítima vem da
     * sessão dela (fix da Task 4), mas a CONTA MP conectada seria a do atacante.
     *
     * <p>Critical 3c (redirect_uri): o Mercado Pago exige que o {@code redirect_uri} da
     * autorização bata com o usado na troca do code por tokens
     * ({@link MercadoPagoOAuthClient#trocarCodePorTokens}) — antes, este método não
     * mandava {@code redirect_uri} nenhum.
     */
    public String gerarUrlAutorizacao(UUID igrejaId, UUID usuarioId) {
        String state = gerarState();
        redisTemplate.opsForValue().set(PREFIXO_STATE + state, usuarioId.toString(), TTL_STATE);

        return "https://auth.mercadopago.com.br/authorization"
            + "?client_id=" + clientId
            + "&response_type=code"
            + "&platform_id=mp"
            + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
            + "&state=" + state;
    }

    /**
     * @throws BusinessException {@code OAUTH_STATE_INVALIDO} quando o {@code state} não
     * existe (expirado, já consumido ou nunca gerado) ou não foi gerado para este mesmo
     * usuário — ver {@link #gerarUrlAutorizacao}.
     */
    public void processarCallback(String code, String state, UUID igrejaId, UUID usuarioId) {
        validarState(state, usuarioId);

        var tokens = client.trocarCodePorTokens(code);
        String accessCriptografado = encryptor.criptografar(tokens.accessToken());
        String refreshCriptografado = encryptor.criptografar(tokens.refreshToken());
        Instant expiraEm = Instant.now().plusSeconds(tokens.expiresInSegundos());

        var contaExistente = repository.findByIgrejaId(igrejaId);
        if (contaExistente.isPresent()) {
            contaExistente.get().atualizarTokens(accessCriptografado, refreshCriptografado, expiraEm);
            repository.save(contaExistente.get());
        } else {
            repository.save(new ContaPagamentoIgreja(
                igrejaId, tokens.mpUserId(), accessCriptografado, refreshCriptografado,
                expiraEm, usuarioId
            ));
        }
    }

    private void validarState(String state, UUID usuarioId) {
        String chave = PREFIXO_STATE + state;
        String usuarioIdGuardado = redisTemplate.opsForValue().get(chave);
        // Uso único: consome o state já na checagem, aprovado ou não — nunca reaproveitável.
        redisTemplate.delete(chave);

        if (usuarioIdGuardado == null || !usuarioIdGuardado.equals(usuarioId.toString())) {
            throw new BusinessException("OAUTH_STATE_INVALIDO",
                "Esta autorização expirou ou é inválida. Tente conectar a conta novamente.");
        }
    }

    private String gerarState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean status(UUID igrejaId) {
        return repository.findByIgrejaId(igrejaId).isPresent();
    }

    /**
     * Critical 4 (revisão final de branch): {@code deleteByIgrejaId} é um derived delete
     * method do Spring Data JPA — precisa rodar dentro de uma transação, senão quebra em
     * runtime (nem o service nem o controller tinham {@code @Transactional} antes).
     */
    @Transactional
    public void desconectar(UUID igrejaId) {
        repository.deleteByIgrejaId(igrejaId);
    }
}
