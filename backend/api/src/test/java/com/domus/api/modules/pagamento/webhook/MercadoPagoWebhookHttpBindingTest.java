package com.domus.api.modules.pagamento.webhook;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.domus.api.modules.pagamento.MercadoPagoApi;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobre o gap apontado na revisão da Task 10: os testes de {@link MercadoPagoWebhookController}
 * chamavam o método Java direto, então nunca exercitaram o binding HTTP real do Spring MVC
 * (`@RequestHeader`/`@RequestParam`). Requisições atípicas do Mercado Pago (o botão "Simular
 * notificação" do painel, um ping de verificação de URL ao cadastrar o webhook, ou o formato
 * real divergindo do assumido) podem chegar sem `data.id`/`type`/`x-signature`/`x-request-id`
 * — e ANTES desta correção, isso fazia o Spring lançar `MissingServletRequestParameterException`/
 * `MissingRequestHeaderException` antes mesmo do método do controller rodar, o que o
 * `GlobalExceptionHandler` (que não trata esses tipos) devolvia como 500 — exatamente o loop de
 * reenvio infinito que o contrato "sempre 200" do webhook existe pra evitar. Este teste dispara
 * a requisição via `MockMvc` (passa pelo `DispatcherServlet` de verdade), não pelo controller
 * Java direto, então teria pegado a regressão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MercadoPagoWebhookHttpBindingTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;

    @MockitoBean MercadoPagoAssinaturaValidator validator;
    @MockitoBean MercadoPagoWebhookService service;
    @MockitoBean MercadoPagoClient mercadoPagoClient;

    @Test
    void respondeOkMesmoSemHeaderDeAssinatura() throws Exception {
        // Nenhum header x-signature/x-request-id — o binding do Spring não pode
        // rejeitar isso antes do controller decidir o que fazer.
        mockMvc.perform(post("/pagamentos/mercadopago/webhook")
                        .param("data.id", "999")
                        .param("type", "payment"))
                .andExpect(status().isOk());

        verify(service, never()).confirmarPagamento(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void respondeOkMesmoSemDataIdNemType() throws Exception {
        // Simula um ping de verificação de URL do painel do Mercado Pago, sem
        // nenhum dos query params esperados.
        mockMvc.perform(post("/pagamentos/mercadopago/webhook")
                        .header("x-signature", "ts=1,v1=hash")
                        .header("x-request-id", "req-1"))
                .andExpect(status().isOk());

        verify(service, never()).confirmarPagamento(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requisicaoCompletaEValidaConfirmaPagamento() throws Exception {
        when(validator.valida("ts=1,v1=hash", "999", "req-1")).thenReturn(true);
        when(mercadoPagoClient.buscarInformacoesPagamentoPorMpUserId("mp-user-1", "999"))
                .thenReturn(new MercadoPagoApi.InformacoesPagamento("cobranca-abc", "approved"));

        mockMvc.perform(post("/pagamentos/mercadopago/webhook")
                        .header("x-signature", "ts=1,v1=hash")
                        .header("x-request-id", "req-1")
                        .param("data.id", "999")
                        .param("type", "payment")
                        .param("user_id", "mp-user-1"))
                .andExpect(status().isOk());

        verify(service).confirmarPagamento("cobranca-abc", "999", "approved");
    }
}
