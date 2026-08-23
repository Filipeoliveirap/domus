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

    public void estornar(UUID igrejaId, String mpPaymentId) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        api.estornar(accessToken, mpPaymentId);
    }

    private String obterAccessTokenPlano(UUID igrejaId) {
        var conta = contaRepository.findByIgrejaId(igrejaId)
            .orElseThrow(() -> new BusinessException("IGREJA_SEM_CONTA_PAGAMENTO",
                "Esta igreja ainda não conectou uma conta para receber pagamentos."));
        return encryptor.descriptografar(conta.getAccessTokenCriptografado());
    }
}
