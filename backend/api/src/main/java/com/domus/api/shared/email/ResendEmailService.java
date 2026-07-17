package com.domus.api.shared.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementação real via Resend (SDK oficial). Ativa quando email.provider=resend.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "email.provider", havingValue = "resend")
public class ResendEmailService implements EmailService {

    private final Resend resend;
    private final String remetente;

    public ResendEmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${email.remetente}") String remetente) {
        this.resend = new Resend(apiKey);
        this.remetente = remetente;
    }

    @Override
    public void enviar(String destinatario, String assunto, String corpoHtml) {
        CreateEmailOptions opcoes = CreateEmailOptions.builder()
                .from(remetente)
                .to(destinatario)
                .subject(assunto)
                .html(corpoHtml)
                .build();
        try {
            CreateEmailResponse resposta = resend.emails().send(opcoes);
            log.info("E-mail enviado via Resend. id={}, para={}", resposta.getId(), destinatario);
        } catch (ResendException e) {
            log.error("Falha ao enviar e-mail via Resend. para={}", destinatario, e);
            throw new RuntimeException("Falha ao enviar e-mail", e);
        }
    }
}
