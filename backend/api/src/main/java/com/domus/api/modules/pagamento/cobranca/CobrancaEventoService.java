package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.modules.pagamento.CalculadoraTaxaPagamento;
import com.domus.api.modules.pagamento.MeioPagamento;
import com.domus.api.modules.pagamento.cobranca.DTOs.OpcoesPagamentoResponse;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CobrancaEventoService {

    public static final Duration PRAZO_PAGAMENTO_IMEDIATO = Duration.ofMinutes(30);
    public static final Duration PRAZO_LINK_COMPARTILHADO = Duration.ofHours(48);

    private final CobrancaEventoRepository repository;
    private final CalculadoraTaxaPagamento calculadora;
    private final SecureRandom random = new SecureRandom();

    public CobrancaEventoService(CobrancaEventoRepository repository,
                                 CalculadoraTaxaPagamento calculadora) {
        this.repository = repository;
        this.calculadora = calculadora;
    }

    /**
     * Monta as opções de pagamento (Pix + faixas de cartão) pra a tela de escolha de método.
     * PIX sempre entra primeiro. Cartão só quando {@code aceitaCartao}, nas faixas 1..teto
     * (teto = min(maxParcelas, 12)). Cada opção já vem com o total gross-up (taxa do MP
     * embutida) recalculado no back — o front só renderiza.
     */
    public OpcoesPagamentoResponse montarOpcoes(BigDecimal valorEvento, boolean aceitaCartao,
                                                int maxParcelas, UUID igrejaId) {
        List<OpcoesPagamentoResponse.OpcaoPagamento> opcoes = new ArrayList<>();

        opcoes.add(opcao(igrejaId, valorEvento, MeioPagamento.PIX, 1));

        if (aceitaCartao) {
            int teto = Math.max(1, Math.min(maxParcelas, 12));
            for (int p = 1; p <= teto; p++) {
                opcoes.add(opcao(igrejaId, valorEvento, MeioPagamento.CARTAO, p));
            }
        }
        return new OpcoesPagamentoResponse(valorEvento, opcoes);
    }

    private OpcoesPagamentoResponse.OpcaoPagamento opcao(UUID igrejaId, BigDecimal alvo,
                                                        MeioPagamento meio, int parcelas) {
        BigDecimal total = calculadora.valorACobrar(igrejaId, alvo, meio, parcelas);
        BigDecimal parcela = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        return new OpcoesPagamentoResponse.OpcaoPagamento(
            meio, parcelas, total, parcela, calculadora.taxaEmReais(alvo, total));
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
