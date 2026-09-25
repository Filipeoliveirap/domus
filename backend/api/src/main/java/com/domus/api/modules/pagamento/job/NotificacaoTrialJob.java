package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoTrialJob {

    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Scheduled(cron = "0 0 8 * * *") // Diariamente às 08:00
    @Transactional(readOnly = true)
    public void notificarExpiracaoTrial() {
        log.info("Iniciando job de notificação diária de expiração de trial...");

        List<Igreja> todasIgrejas = igrejaRepository.findAll();
        LocalDateTime agora = LocalDateTime.now();

        for (Igreja igreja : todasIgrejas) {
            if (igreja.getStatusAssinatura() == StatusAssinatura.TRIAL && igreja.getTrialExpiraEm() != null) {
                long diasRestantes = ChronoUnit.DAYS.between(agora.toLocalDate(), igreja.getTrialExpiraEm().toLocalDate());

                if (diasRestantes == 7 || diasRestantes == 3 || diasRestantes == 1) {
                    processarNotificacaoIgreja(igreja, (int) diasRestantes);
                }
            }
        }
        log.info("Job de notificação diária de expiração de trial concluído.");
    }

    private void processarNotificacaoIgreja(Igreja igreja, int diasRestantes) {
        Set<String> destinatarios = new HashSet<>();

        if (igreja.getEmailContato() != null && !igreja.getEmailContato().isBlank()) {
            destinatarios.add(igreja.getEmailContato().toLowerCase().trim());
        }

        List<String> emailsAdmins = usuarioRepository.buscarEmailsAdminsAtivos(igreja.getId());
        for (String email : emailsAdmins) {
            if (email != null && !email.isBlank()) {
                destinatarios.add(email.toLowerCase().trim());
            }
        }

        if (destinatarios.isEmpty()) {
            log.warn("Nenhum e-mail de destinatário encontrado para notificar trial. igreja_id={}", igreja.getId());
            return;
        }

        String dataFinalFormatada = igreja.getTrialExpiraEm().format(DATE_FORMATTER);
        String termoDias = (diasRestantes == 1) ? "amanhã" : "em " + diasRestantes + " dias";
        String assunto = (diasRestantes == 1) 
                ? "Seu plano grátis acaba amanhã! - Domus" 
                : String.format("Seu plano grátis acaba em %d dias! - Domus", diasRestantes);

        String mensagemVisual = String.format(
                "Seu plano grátis de 14 dias acaba %s. Você tem até o dia %s para usar gratuitamente, após esse período será descontado automaticamente da conta.",
                termoDias, dataFinalFormatada
        );

        String corpoHtml = String.format("""
                <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; rounded: 8px;">
                    <h2 style="color: #4f46e5;">Domus - Período de Teste Grátis</h2>
                    <p style="font-size: 16px; color: #334155;">Olá, <strong>%s</strong>!</p>
                    <p style="font-size: 15px; color: #475569; line-height: 1.6;">%s</p>
                    <div style="margin-top: 25px; padding: 15px; background-color: #f8fafc; border-left: 4px solid #4f46e5; border-radius: 4px;">
                        <p style="margin: 0; font-size: 14px; color: #64748b;">Dúvidas ou precisa de suporte? Entre em contato com nossa equipe.</p>
                    </div>
                </div>
                """, igreja.getNome(), mensagemVisual);

        for (String destinatario : destinatarios) {
            try {
                emailService.enviar(destinatario, assunto, corpoHtml);
                log.info("E-mail de trial enviado. igreja_id={}, destinatario={}, diasRestantes={}", igreja.getId(), destinatario, diasRestantes);
            } catch (Exception e) {
                log.error("Erro ao enviar e-mail de trial. igreja_id={}, destinatario={}", igreja.getId(), destinatario, e);
            }
        }
    }
}
