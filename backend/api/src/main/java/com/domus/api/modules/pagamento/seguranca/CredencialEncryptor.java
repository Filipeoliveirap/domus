package com.domus.api.modules.pagamento.seguranca;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM com IV aleatório por chamada. A chave vem só de variável de ambiente
 * (nunca do banco, nunca logada) — primeira credencial reversível de terceiro que o
 * projeto guarda (access_token/refresh_token do Mercado Pago da igreja).
 */
@Component
public class CredencialEncryptor {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CredencialEncryptor(@Value("${app.pagamento.encryption-key}") String chaveBase64) {
        byte[] bytesChave = Base64.getDecoder().decode(chaveBase64);
        this.chave = new SecretKeySpec(bytesChave, "AES");
    }

    public String criptografar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] textoCifrado = cipher.doFinal(textoPlano.getBytes());

            byte[] resultado = new byte[iv.length + textoCifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(textoCifrado, 0, resultado, iv.length, textoCifrado.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar credencial", e);
        }
    }

    public String descriptografar(String textoCriptografado) {
        try {
            byte[] bytes = Base64.getDecoder().decode(textoCriptografado);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            byte[] textoCifrado = new byte[bytes.length - TAMANHO_IV_BYTES];
            System.arraycopy(bytes, 0, iv, 0, iv.length);
            System.arraycopy(bytes, iv.length, textoCifrado, 0, textoCifrado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(textoCifrado));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar credencial", e);
        }
    }
}
