package com.domus.api.modules.pagamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CredencialEncryptorTest {

    // Chave AES-256 de teste, 32 bytes em Base64 — nunca usar em produção.
    private static final String CHAVE_TESTE = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    private final CredencialEncryptor encryptor = new CredencialEncryptor(CHAVE_TESTE);

    @Test
    void criptografaEDescriptografaDeVolta() {
        String original = "APP_USR-1234567890-mercadopago-access-token";

        String criptografado = encryptor.criptografar(original);
        String descriptografado = encryptor.descriptografar(criptografado);

        assertThat(criptografado).isNotEqualTo(original);
        assertThat(descriptografado).isEqualTo(original);
    }

    @Test
    void criptografiasSucessivasDoMesmoValorGeramSaidasDiferentes() {
        // AES-GCM usa IV aleatório por chamada — mesma entrada, saída diferente.
        // Prova que não é um cifrador determinístico (o que vazaria padrão).
        String original = "mesmo-valor";

        String primeira = encryptor.criptografar(original);
        String segunda = encryptor.criptografar(original);

        assertThat(primeira).isNotEqualTo(segunda);
        assertThat(encryptor.descriptografar(primeira)).isEqualTo(original);
        assertThat(encryptor.descriptografar(segunda)).isEqualTo(original);
    }

    @Test
    void recusaDescriptografarValorAdulterado() {
        String criptografado = encryptor.criptografar("valor-original");
        String adulterado = criptografado.substring(0, criptografado.length() - 4) + "AAAA";

        assertThatThrownBy(() -> encryptor.descriptografar(adulterado))
            .isInstanceOf(IllegalStateException.class);
    }
}
