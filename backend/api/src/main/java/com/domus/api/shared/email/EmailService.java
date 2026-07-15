package com.domus.api.shared.email;

/**
 * Contrato de envio de e-mail transacional. O resto do sistema depende só desta
 * interface — a troca de provedor (Resend, SES, ...) é uma questão de qual
 * implementação está ativa, decidida por configuração (email.provider).
 */
public interface EmailService {

    void enviar(String destinatario, String assunto, String corpoHtml);
}
