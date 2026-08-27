package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CobrancaEventoService {

    public static final Duration PRAZO_PAGAMENTO_IMEDIATO = Duration.ofMinutes(30);
    public static final Duration PRAZO_LINK_COMPARTILHADO = Duration.ofHours(48);

    private final CobrancaEventoRepository repository;
    private final SecureRandom random = new SecureRandom();

    public CobrancaEventoService(CobrancaEventoRepository repository) {
        this.repository = repository;
    }

    public CobrancaEvento criarParaTitular(UUID igrejaId, UUID eventoId, UUID inscricaoId,
                                            UUID pessoaId, BigDecimal valor, UUID criadoPorUsuarioId) {
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, valor,
            Instant.now().plus(PRAZO_PAGAMENTO_IMEDIATO), criadoPorUsuarioId, null);
        return repository.save(cobranca);
    }

    public CobrancaEvento criarParaTerceiro(UUID igrejaId, UUID eventoId, UUID inscricaoId,
                                             UUID pessoaId, BigDecimal valor,
                                             UUID criadoPorUsuarioId, boolean gerarLink) {
        String token = gerarLink ? gerarToken() : null;
        Duration prazo = gerarLink ? PRAZO_LINK_COMPARTILHADO : PRAZO_PAGAMENTO_IMEDIATO;

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            valor, Instant.now().plus(prazo), criadoPorUsuarioId, token);
        return repository.save(cobranca);
    }

    public CobrancaEvento buscarPorToken(String token) {
        return repository.findByTokenLinkPublico(token)
            .orElseThrow(() -> new BusinessException("LINK_COBRANCA_INVALIDO",
                "Este link de pagamento não existe ou expirou."));
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
