package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

/** Roda uma vez por dia: lembra em D-5 e D-1, e (a partir da Fase 2) executa a purga em D-10. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExclusaoIgrejaJob {

    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final PurgaIgrejaService purgaIgrejaService;
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Mesmo link da Zona de Perigo usado no e-mail de agendamento — funciona logado ou não. */
    private String linkCancelarExclusao() {
        return frontendUrl + "/login?next=/configuracoes/igreja";
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void verificarPrazos() {
        var igrejas = igrejaRepository.buscarComExclusaoAgendada();
        int lembretes = 0;

        for (Igreja igreja : igrejas) {
            try {
                long decorridos = ChronoUnit.DAYS.between(igreja.getExclusaoAgendadaEm(), LocalDateTime.now());
                long faltam = 10 - decorridos;

                if (faltam == 5) {
                    enviarLembrete(igreja, "Sua igreja será excluída em 5 dias", "Faltam 5 dias");
                    lembretes++;
                } else if (faltam == 1) {
                    enviarLembrete(igreja, "Sua igreja será excluída em 1 dia", "Falta 1 dia");
                    lembretes++;
                } else if (faltam <= 0) {
                    purgaIgrejaService.purgar(igreja.getId());
                }
            } catch (Exception e) {
                // Cada igreja é independente: uma falha aqui não pode derrubar as demais
                // (nem os lembretes já enviados nesta rodada) — o job tenta de novo amanhã.
                log.error("Falha ao processar exclusão agendada de uma igreja — seguindo para as demais. igreja_id={}",
                        igreja.getId(), e);
            }
        }

        log.info("Verificação diária de exclusões agendadas concluída. igrejas_agendadas={}, lembretes_enviados={}",
                igrejas.size(), lembretes);
    }

    /** Avisa o contato da igreja E todos os ADMIN_IGREJA ativos, não só quem agendou. */
    private void enviarLembrete(Igreja igreja, String assunto, String prazoTexto) {
        String corpo = corpoEmailLembrete(igreja.getNome(), prazoTexto);
        Set<String> destinatarios = new LinkedHashSet<>();
        if (igreja.getEmailContato() != null && !igreja.getEmailContato().isBlank()) {
            destinatarios.add(igreja.getEmailContato());
        }
        destinatarios.addAll(usuarioRepository.buscarEmailsAdminsAtivos(igreja.getId()));

        for (String destinatario : destinatarios) {
            emailService.enviar(destinatario, assunto, corpo);
        }

        java.util.List<com.domus.api.modules.usuario.Usuario> admins = usuarioRepository
                .findByIgrejaIdAndRole_NomeAndAtivoTrue(igreja.getId(),
                        com.domus.api.shared.security.Perfil.ADMIN_IGREJA.name());
        for (var admin : admins) {
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.EXCLUSAO_IGREJA_AGENDADA,
                    igreja.getId(), admin.getId(), assunto, "/configuracoes/igreja");
        }
    }

    private String corpoEmailLembrete(String nomeIgreja, String prazoTexto) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <h2>%s para a exclusão</h2>
                  <p>%s para a exclusão definitiva de "%s". Depois disso, não há como recuperar.</p>
                  <p>Se quiser manter sua conta, cancele agora:</p>
                  <p style="text-align: center; margin: 32px 0;">
                    <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 24px;
                       text-decoration: none; border-radius: 6px;">Cancelar exclusão</a>
                  </p>
                </div>
                """.formatted(prazoTexto, prazoTexto, nomeIgreja, linkCancelarExclusao());
    }
}
