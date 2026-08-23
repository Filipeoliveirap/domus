package com.domus.api.modules.pagamento.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoOAuthServiceTest {

    ContaPagamentoIgrejaRepository repository;
    CredencialEncryptor encryptor;
    MercadoPagoOAuthClient client; // wrapper do SDK que troca `code` por tokens — mockado aqui
    MercadoPagoOAuthService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(ContaPagamentoIgrejaRepository.class);
        encryptor = mock(CredencialEncryptor.class);
        client = mock(MercadoPagoOAuthClient.class);
        service = new MercadoPagoOAuthService(repository, encryptor, client, "client-id-teste");
    }

    @Test
    void geraUrlDeAutorizacaoComIgrejaIdNoState() {
        String url = service.gerarUrlAutorizacao(igrejaId);

        assertThat(url).contains("client_id=client-id-teste");
        assertThat(url).contains("state=" + igrejaId);
    }

    @Test
    void processaCallbackSalvandoTokensCriptografados() {
        var tokensObtidos = new MercadoPagoOAuthClient.TokensObtidos(
            "mp-user-999", "access-plano", "refresh-plano", 21600L
        );
        when(client.trocarCodePorTokens("code-123")).thenReturn(tokensObtidos);
        when(encryptor.criptografar("access-plano")).thenReturn("access-cripto");
        when(encryptor.criptografar("refresh-plano")).thenReturn("refresh-cripto");
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        service.processarCallback("code-123", igrejaId, usuarioId);

        verify(repository).save(argThat(conta ->
            conta.getIgrejaId().equals(igrejaId) &&
            conta.getMpUserId().equals("mp-user-999") &&
            conta.getAccessTokenCriptografado().equals("access-cripto") &&
            conta.getRefreshTokenCriptografado().equals("refresh-cripto")
        ));
    }

    @Test
    void processarCallbackAtualizaContaExistenteEmVezDeDuplicar() {
        var contaExistente = new ContaPagamentoIgreja(
            igrejaId, "mp-user-antigo", "antigo-access", "antigo-refresh",
            Instant.now(), usuarioId
        );
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(contaExistente));
        var tokensObtidos = new MercadoPagoOAuthClient.TokensObtidos(
            "mp-user-999", "access-novo", "refresh-novo", 21600L
        );
        when(client.trocarCodePorTokens("code-123")).thenReturn(tokensObtidos);
        when(encryptor.criptografar(any())).thenReturn("cripto");

        service.processarCallback("code-123", igrejaId, usuarioId);

        verify(repository, never()).save(argThat(c -> c != contaExistente));
        verify(repository).save(contaExistente);
    }

    @Test
    void statusRetornaFalsoQuandoIgrejaNaoTemContaConectada() {
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThat(service.status(igrejaId)).isFalse();
    }

    @Test
    void statusRetornaVerdadeiroQuandoIgrejaTemContaConectada() {
        when(repository.findByIgrejaId(igrejaId)).thenReturn(
            Optional.of(new ContaPagamentoIgreja(igrejaId, "mp-user", "a", "r", Instant.now(), usuarioId))
        );

        assertThat(service.status(igrejaId)).isTrue();
    }

    @Test
    void desconectarRemoveAConta() {
        service.desconectar(igrejaId);

        verify(repository).deleteByIgrejaId(igrejaId);
    }
}
