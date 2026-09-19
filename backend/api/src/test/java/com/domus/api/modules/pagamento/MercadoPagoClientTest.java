package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoClientTest {

    ContaPagamentoIgrejaRepository contaRepository;
    CredencialEncryptor encryptor;
    MercadoPagoApi api; // wrapper fino da chamada HTTP real, mockado aqui
    MercadoPagoClient client;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaPagamentoIgrejaRepository.class);
        encryptor = mock(CredencialEncryptor.class);
        api = mock(MercadoPagoApi.class);
        client = new MercadoPagoClient(contaRepository, encryptor, api);
    }

    @Test
    void lancaErroDeNegocioQuandoIgrejaNaoTemContaConectada() {
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> client.buscarQrCodePix(igrejaId, "mp-payment-999"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");
    }

    @Test
    void buscarQrCodePixUsaTokenDescriptografadoDaIgreja() {
        var conta = new ContaPagamentoIgreja(igrejaId, "mp-user", "access-cripto", "refresh-cripto",
            Instant.now().plusSeconds(3600), UUID.randomUUID());
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        when(encryptor.descriptografar("access-cripto")).thenReturn("access-plano");
        when(api.buscarQrCodePix("access-plano", "mp-payment-999"))
            .thenReturn(new MercadoPagoApi.QrCodePix("codigo-copia-cola", "base64-do-qr", java.time.Instant.now().plusSeconds(1800)));

        var resultado = client.buscarQrCodePix(igrejaId, "mp-payment-999");

        assertThat(resultado.qrCode()).isEqualTo("codigo-copia-cola");
        assertThat(resultado.qrCodeBase64()).isEqualTo("base64-do-qr");
    }

    @Test
    void estornarParcialUsaTokenDescriptografadoDaIgreja() {
        var conta = new ContaPagamentoIgreja(igrejaId, "mp-user", "access-cripto", "refresh-cripto",
            Instant.now().plusSeconds(3600), UUID.randomUUID());
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        when(encryptor.descriptografar("access-cripto")).thenReturn("access-plano");

        client.estornarParcial(igrejaId, "mp-payment-999", new java.math.BigDecimal("30.00"));

        verify(api).estornarParcial("access-plano", "mp-payment-999", new java.math.BigDecimal("30.00"));
    }
}
