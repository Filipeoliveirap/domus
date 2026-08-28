package com.domus.api.modules.pagamento.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class MercadoPagoOAuthServiceTest {

    ContaPagamentoIgrejaRepository repository;
    CredencialEncryptor encryptor;
    MercadoPagoOAuthClient client; // wrapper do SDK que troca `code` por tokens — mockado aqui
    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> valueOperations;
    MercadoPagoOAuthService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(ContaPagamentoIgrejaRepository.class);
        encryptor = mock(CredencialEncryptor.class);
        client = mock(MercadoPagoOAuthClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new MercadoPagoOAuthService(repository, encryptor, client, redisTemplate,
                "client-id-teste", "https://app.teste.com/callback");
    }

    /** Simula o {@code state} já gravado no Redis pra {@code usuarioId} (como {@link #gerarUrlAutorizacao} faria). */
    private void comStateValidoParaUsuario(String state, UUID usuario) {
        when(valueOperations.get("mpoauth:state:" + state)).thenReturn(usuario.toString());
    }

    @Test
    void geraUrlDeAutorizacaoComStateAleatorioERedirectUri() {
        String url = service.gerarUrlAutorizacao(igrejaId, usuarioId);

        assertThat(url).contains("client_id=client-id-teste");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("state=");
        // Critical 3a: state NÃO pode ser o igrejaId em texto — tem que ser um nonce aleatório.
        assertThat(url).doesNotContain("state=" + igrejaId);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("mpoauth:state:"),
                eq(usuarioId.toString()), any(java.time.Duration.class));
    }

    @Test
    void processaCallbackSalvandoTokensCriptografadosQuandoStateEhValido() {
        comStateValidoParaUsuario("state-valido", usuarioId);
        var tokensObtidos = new MercadoPagoOAuthClient.TokensObtidos(
            "mp-user-999", "access-plano", "refresh-plano", 21600L
        );
        when(client.trocarCodePorTokens("code-123")).thenReturn(tokensObtidos);
        when(encryptor.criptografar("access-plano")).thenReturn("access-cripto");
        when(encryptor.criptografar("refresh-plano")).thenReturn("refresh-cripto");
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        service.processarCallback("code-123", "state-valido", igrejaId, usuarioId);

        verify(repository).save(argThat(conta ->
            conta.getIgrejaId().equals(igrejaId) &&
            conta.getMpUserId().equals("mp-user-999") &&
            conta.getAccessTokenCriptografado().equals("access-cripto") &&
            conta.getRefreshTokenCriptografado().equals("refresh-cripto")
        ));
    }

    @Test
    void recusaCallbackQuandoStateNaoExisteOuExpirou() {
        // Critical 3a (revisão final de branch): state nunca gravado (ou já expirado no Redis).
        when(valueOperations.get("mpoauth:state:state-inexistente")).thenReturn(null);

        assertThatThrownBy(() -> service.processarCallback("code-123", "state-inexistente", igrejaId, usuarioId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("codigo", "OAUTH_STATE_INVALIDO");

        verifyNoInteractions(client);
        verify(repository, never()).save(any());
    }

    @Test
    void recusaCallbackQuandoStateFoiGeradoParaOutroUsuario() {
        // Critical 3a: o state existe no Redis, mas foi gerado pra OUTRO usuário — o
        // cenário exato do ataque de CSRF de OAuth que esta correção fecha.
        comStateValidoParaUsuario("state-de-outro", UUID.randomUUID());

        assertThatThrownBy(() -> service.processarCallback("code-123", "state-de-outro", igrejaId, usuarioId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("codigo", "OAUTH_STATE_INVALIDO");

        verifyNoInteractions(client);
    }

    @Test
    void processarCallbackAtualizaContaExistenteEmVezDeDuplicar() {
        comStateValidoParaUsuario("state-valido", usuarioId);
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

        service.processarCallback("code-123", "state-valido", igrejaId, usuarioId);

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

    @Test
    void renovarTokenDaContaAtualizaTokensCriptografados() {
        var conta = new ContaPagamentoIgreja(
            igrejaId, "mp-user-999", "access-antigo-cripto", "refresh-antigo-cripto",
            Instant.now(), usuarioId
        );
        when(encryptor.descriptografar("refresh-antigo-cripto")).thenReturn("refresh-antigo-plano");
        when(client.renovarToken("refresh-antigo-plano")).thenReturn(
            new MercadoPagoOAuthClient.TokensObtidos("mp-user-999", "access-novo", "refresh-novo", 21600L)
        );
        when(encryptor.criptografar("access-novo")).thenReturn("access-novo-cripto");
        when(encryptor.criptografar("refresh-novo")).thenReturn("refresh-novo-cripto");

        service.renovarTokenDaConta(conta);

        assertThat(conta.getAccessTokenCriptografado()).isEqualTo("access-novo-cripto");
        assertThat(conta.getRefreshTokenCriptografado()).isEqualTo("refresh-novo-cripto");
        verify(repository).save(conta);
    }

    @Test
    void renovarTokenDaContaPropagaExcecaoQuandoRefreshTokenTambemVenceu() {
        // Sem refresh token válido não tem como automatizar mais nada — quem chama (o job)
        // decide notificar a igreja pra reconectar manualmente.
        var conta = new ContaPagamentoIgreja(
            igrejaId, "mp-user-999", "access-antigo-cripto", "refresh-antigo-cripto",
            Instant.now(), usuarioId
        );
        when(encryptor.descriptografar("refresh-antigo-cripto")).thenReturn("refresh-antigo-plano");
        when(client.renovarToken("refresh-antigo-plano")).thenThrow(new IllegalStateException("refresh_token inválido"));

        assertThatThrownBy(() -> service.renovarTokenDaConta(conta)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).save(any());
    }
}
