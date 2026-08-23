package com.domus.api.modules.pagamento;

import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Resolve a conta de pagamento (Mercado Pago) da igreja e delega a chamada real a
 * {@link MercadoPagoApi}. O access token só existe em texto plano dentro deste service,
 * pelo tempo da chamada — nunca é logado nem retornado.
 */
@Service
public class MercadoPagoClient {

    private final ContaPagamentoIgrejaRepository contaRepository;
    private final CredencialEncryptor encryptor;
    private final MercadoPagoApi api;

    public MercadoPagoClient(ContaPagamentoIgrejaRepository contaRepository,
                              CredencialEncryptor encryptor, MercadoPagoApi api) {
        this.contaRepository = contaRepository;
        this.encryptor = encryptor;
        this.api = api;
    }

    public String criarPagamento(UUID igrejaId, CobrancaEvento cobranca) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        return api.criarPagamento(accessToken, cobranca.getId().toString(), cobranca.getValor());
    }

    /**
     * Usado pelo endpoint {@code POST /cobrancas/{id}/pagar} (Task 14) — recebe os dados
     * já TOKENIZADOS pelo Payment Brick no navegador do pagador (nunca o número do
     * cartão em si) e delega pra {@link MercadoPagoApi#criarPagamentoTokenizado}. Só
     * INICIA o pagamento no Mercado Pago; a confirmação definitiva (marcar a
     * {@code CobrancaEvento} como PAGO) continua vindo assíncrona, pelo webhook (Task 10).
     */
    public String criarPagamentoComToken(UUID igrejaId, CobrancaEvento cobranca, String token,
                                          String paymentMethodId, Integer installments, String payerEmail) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        return api.criarPagamentoTokenizado(accessToken, cobranca.getId().toString(), cobranca.getValor(),
            token, paymentMethodId, installments, payerEmail);
    }

    public void estornar(UUID igrejaId, String mpPaymentId) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        api.estornar(accessToken, mpPaymentId);
    }

    /**
     * Usado pelo webhook: o Mercado Pago não manda o {@code external_reference} direto no
     * payload de notificação (só {@code data.id}), e o webhook não sabe de antemão de qual
     * igreja é o pagamento — é exatamente isso que esta chamada resolve. O próprio webhook
     * manda o {@code user_id} da conta MP (dona do pagamento) junto do payload/query string;
     * usamos esse id pra achar a {@code ContaPagamentoIgreja} certa (por {@code mp_user_id},
     * não por {@code igreja_id}) e só então temos o access token pra consultar o pagamento.
     */
    public String buscarExternalReferencePorMpUserId(String mpUserId, String mpPaymentId) {
        var conta = contaRepository.findByMpUserId(mpUserId)
            .orElseThrow(() -> new BusinessException("CONTA_PAGAMENTO_NAO_ENCONTRADA",
                "Nenhuma igreja conectada com este mp_user_id."));
        String accessToken = encryptor.descriptografar(conta.getAccessTokenCriptografado());
        return api.buscarExternalReference(accessToken, mpPaymentId);
    }

    private String obterAccessTokenPlano(UUID igrejaId) {
        var conta = contaRepository.findByIgrejaId(igrejaId)
            .orElseThrow(() -> new BusinessException("IGREJA_SEM_CONTA_PAGAMENTO",
                "Esta igreja ainda não conectou uma conta para receber pagamentos."));
        return encryptor.descriptografar(conta.getAccessTokenCriptografado());
    }
}
