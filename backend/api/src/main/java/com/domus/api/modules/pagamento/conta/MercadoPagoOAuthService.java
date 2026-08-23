package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MercadoPagoOAuthService {

    private final ContaPagamentoIgrejaRepository repository;
    private final CredencialEncryptor encryptor;
    private final MercadoPagoOAuthClient client;
    private final String clientId;

    public MercadoPagoOAuthService(ContaPagamentoIgrejaRepository repository,
                                    CredencialEncryptor encryptor,
                                    MercadoPagoOAuthClient client,
                                    @Value("${app.pagamento.mercadopago.client-id}") String clientId) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.client = client;
        this.clientId = clientId;
    }

    public String gerarUrlAutorizacao(UUID igrejaId) {
        String state = URLEncoder.encode(igrejaId.toString(), StandardCharsets.UTF_8);
        return "https://auth.mercadopago.com.br/authorization"
            + "?client_id=" + clientId
            + "&response_type=code"
            + "&platform_id=mp"
            + "&state=" + state;
    }

    public void processarCallback(String code, UUID igrejaId, UUID usuarioId) {
        var tokens = client.trocarCodePorTokens(code);
        String accessCriptografado = encryptor.criptografar(tokens.accessToken());
        String refreshCriptografado = encryptor.criptografar(tokens.refreshToken());
        Instant expiraEm = Instant.now().plusSeconds(tokens.expiresInSegundos());

        var contaExistente = repository.findByIgrejaId(igrejaId);
        if (contaExistente.isPresent()) {
            contaExistente.get().atualizarTokens(accessCriptografado, refreshCriptografado, expiraEm);
            repository.save(contaExistente.get());
        } else {
            repository.save(new ContaPagamentoIgreja(
                igrejaId, tokens.mpUserId(), accessCriptografado, refreshCriptografado,
                expiraEm, usuarioId
            ));
        }
    }

    public boolean status(UUID igrejaId) {
        return repository.findByIgrejaId(igrejaId).isPresent();
    }

    public void desconectar(UUID igrejaId) {
        repository.deleteByIgrejaId(igrejaId);
    }
}
