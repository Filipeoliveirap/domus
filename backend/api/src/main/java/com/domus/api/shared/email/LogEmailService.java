package com.domus.api.shared.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementação de desenvolvimento: não envia nada, apenas imprime o e-mail no log.
 * É a padrão (email.provider=log ou ausente), então o sistema funciona sem nenhuma
 * credencial. Permite testar o fluxo (ex.: reset de senha) sem provedor real.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "log", matchIfMissing = true)
public class LogEmailService implements EmailService {

    @Override
    public void enviar(String destinatario, String assunto, String corpoHtml) {
        log.info("""

                ┌─────────── E-MAIL (modo log, não enviado) ───────────
                │ Para:    {}
                │ Assunto: {}
                │ Corpo:
                {}
                └───────────────────────────────────────────────────────""",
                destinatario, assunto, corpoHtml);
    }
}
