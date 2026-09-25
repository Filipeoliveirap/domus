package com.domus.api.modules.admin;

import com.domus.api.modules.admin.dto.ConfiguracaoMercadoPagoDTO;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminConfiguracaoServiceTest {

    @Mock
    private ConfiguracaoPlataformaRepository repository;

    @Mock
    private CredencialEncryptor encryptor;

    @InjectMocks
    private AdminConfiguracaoService configuracaoService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(configuracaoService, "envAccessToken", "APP_USR-env-token-default");
        ReflectionTestUtils.setField(configuracaoService, "envPublicKey", "APP_USR-env-public-key");
    }

    @Test
    @DisplayName("obterAccessTokenAtivo_retornaTokenDoBancoCriptografado")
    void obterAccessTokenAtivo_retornaTokenDoBancoCriptografado() {
        ConfiguracaoPlataforma config = ConfiguracaoPlataforma.builder()
                .chave("MP_ACCESS_TOKEN")
                .valorCriptografado("token_cripto")
                .build();

        when(repository.findById("MP_ACCESS_TOKEN")).thenReturn(Optional.of(config));
        when(encryptor.descriptografar("token_cripto")).thenReturn("APP_USR-1234567890-token-real");

        String token = configuracaoService.obterAccessTokenAtivo();

        assertEquals("APP_USR-1234567890-token-real", token);
    }

    @Test
    @DisplayName("obterAccessTokenAtivo_semBanco_retornaFallbackEnvvar")
    void obterAccessTokenAtivo_semBanco_retornaFallbackEnvvar() {
        when(repository.findById("MP_ACCESS_TOKEN")).thenReturn(Optional.empty());

        String token = configuracaoService.obterAccessTokenAtivo();

        assertEquals("APP_USR-env-token-default", token);
    }

    @Test
    @DisplayName("salvarConfiguracaoMercadoPago_criptografaESalvaChaves")
    void salvarConfiguracaoMercadoPago_criptografaESalvaChaves() {
        ConfiguracaoMercadoPagoDTO dto = ConfiguracaoMercadoPagoDTO.builder()
                .accessToken("APP_USR-novo-token-secret")
                .publicKey("APP_USR-nova-public-key")
                .build();

        when(encryptor.criptografar("APP_USR-novo-token-secret")).thenReturn("access_crip");
        when(encryptor.criptografar("APP_USR-nova-public-key")).thenReturn("public_crip");

        configuracaoService.salvarConfiguracaoMercadoPago(dto);

        verify(repository, times(2)).save(any(ConfiguracaoPlataforma.class));
    }
}
