package com.domus.api.modules.admin;

import com.domus.api.modules.admin.dto.ConfiguracaoMercadoPagoDTO;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminConfiguracaoService {

    private static final String CHAVE_MP_ACCESS_TOKEN = "MP_ACCESS_TOKEN";
    private static final String CHAVE_MP_PUBLIC_KEY = "MP_PUBLIC_KEY";

    private final ConfiguracaoPlataformaRepository repository;
    private final CredencialEncryptor encryptor;

    @Value("${app.pagamento.mercadopago.access-token:}")
    private String envAccessToken;

    @Value("${app.pagamento.mercadopago.public-key:}")
    private String envPublicKey;

    @Transactional(readOnly = true)
    public ConfiguracaoMercadoPagoDTO obterConfiguracaoMercadoPago() {
        var optAccess = repository.findById(CHAVE_MP_ACCESS_TOKEN);
        var optPublic = repository.findById(CHAVE_MP_PUBLIC_KEY);

        String access = optAccess.map(c -> encryptor.descriptografar(c.getValorCriptografado())).orElse(envAccessToken);
        String publicKey = optPublic.map(c -> encryptor.descriptografar(c.getValorCriptografado())).orElse(envPublicKey);

        boolean configurado = access != null && !access.isBlank();

        // Encurtar token na exibição para não vazar a credencial inteira na UI
        String accessMascarado = configurado && access.length() > 10 
                ? access.substring(0, 6) + "..." + access.substring(access.length() - 4) 
                : access;

        return ConfiguracaoMercadoPagoDTO.builder()
                .accessToken(accessMascarado)
                .publicKey(publicKey)
                .configurado(configurado)
                .build();
    }

    @Transactional
    public ConfiguracaoMercadoPagoDTO salvarConfiguracaoMercadoPago(ConfiguracaoMercadoPagoDTO dto) {
        if (dto.getAccessToken() != null && !dto.getAccessToken().isBlank() && !dto.getAccessToken().contains("...")) {
            String accessCrip = encryptor.criptografar(dto.getAccessToken().trim());
            repository.save(ConfiguracaoPlataforma.builder()
                    .chave(CHAVE_MP_ACCESS_TOKEN)
                    .valorCriptografado(accessCrip)
                    .build());
        }

        if (dto.getPublicKey() != null && !dto.getPublicKey().isBlank()) {
            String publicCrip = encryptor.criptografar(dto.getPublicKey().trim());
            repository.save(ConfiguracaoPlataforma.builder()
                    .chave(CHAVE_MP_PUBLIC_KEY)
                    .valorCriptografado(publicCrip)
                    .build());
        }

        log.info("Credenciais de recebimento do Mercado Pago da plataforma Domus atualizadas via painel admin.");
        return obterConfiguracaoMercadoPago();
    }

    /** Retorna o Access Token ativo (do banco de dados ou envvar de fallback) */
    @Transactional(readOnly = true)
    public String obterAccessTokenAtivo() {
        return repository.findById(CHAVE_MP_ACCESS_TOKEN)
                .map(c -> encryptor.descriptografar(c.getValorCriptografado()))
                .filter(a -> !a.isBlank())
                .orElse(envAccessToken);
    }
}
