package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoResponsavel;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Lógica das 3 notificações de prazo de inscrição (V38) + carimbos de idempotência.
 *  Bean separado do {@link PrazoInscricaoJob} de propósito: o job chama
 *  {@code processar} através do proxy Spring, então o {@link Transactional} abre uma
 *  transação de verdade por evento (self-invocation dentro do próprio job não abriria).
 *  Idempotente: cada aviso tem um carimbo no próprio registro
 *  (evento.avisoPrazoProximoEm / evento.avisoPrazoFechadoEm /
 *  inscricao.avisoPrazoIncompletoEm), então rodar 2x no mesmo dia não duplica. */
@Component
@RequiredArgsConstructor
public class PrazoInscricaoProcessador {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;

    @Value("${app.eventos.aviso-prazo-dias:3}")
    private int avisoPrazoDias;

    @Transactional
    public void processar(java.util.UUID eventoId, LocalDateTime agora) {
        // O job não é transacional: o Evento vindo de buscarComPrazoAtivoParaJob chega
        // DESTACADO. Recarrega dentro desta transação pra getResponsaveis() (LAZY) não
        // estourar LazyInitializationException. Ver memória "Principal desanexado + LAZY".
        Evento evento = eventoRepository.findById(eventoId).orElse(null);
        if (evento == null) return;
        if (evento.getInscricoesAte() == null) return;

        LocalDateTime prazo = evento.getInscricoesAte();
        boolean venceu = agora.isAfter(prazo);
        boolean naJanela = !venceu && agora.isAfter(prazo.minusDays(avisoPrazoDias));

        // 1) lembrete de inscrição incompleta (evento pago) — só na janela do "chegando"
        if (naJanela) {
            for (InscricaoEvento inscricao : inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())) {
                if (inscricao.getPessoa() != null) {
                    usuarioRepository.findByPessoaId(inscricao.getPessoa().getId()).ifPresent(usuario ->
                        notificacaoService.criar(TipoNotificacao.PRAZO_INSCRICAO_INCOMPLETA,
                            evento.getIgreja().getId(), usuario.getId(),
                            "O prazo de inscrição em \"" + evento.getTitulo() + "\" encerra em "
                                + prazo.format(DATA) + ". Falta pagar pra garantir sua vaga.",
                            "/eventos?detalhe=" + evento.getId()));
                }
                inscricao.setAvisoPrazoIncompletoEm(agora);
                inscricaoRepository.save(inscricao);
            }
        }

        // 2) aviso ao(s) responsável(is): prazo chegando
        if (naJanela && evento.getAvisoPrazoProximoEm() == null) {
            long dias = Math.max(0, Duration.between(agora, prazo).toDays());
            notificarResponsaveis(evento, TipoNotificacao.PRAZO_INSCRICAO_PROXIMO,
                "As inscrições de \"" + evento.getTitulo() + "\" encerram em " + prazo.format(DATA)
                    + " (" + dias + " dia" + (dias == 1 ? "" : "s") + ").",
                "/eventos?detalhe=" + evento.getId());
            evento.setAvisoPrazoProximoEm(agora);
            eventoRepository.save(evento);
        }

        // 3) aviso ao(s) responsável(is): prazo fechou
        if (venceu && evento.getAvisoPrazoFechadoEm() == null) {
            long confirmadas = inscricaoRepository.contarPorEventoIdEStatus(
                evento.getId(), StatusInscricao.CONFIRMADA);
            long aguardando = inscricaoRepository.contarPorEventoIdEStatus(
                evento.getId(), StatusInscricao.AGUARDANDO_PAGAMENTO);
            notificarResponsaveis(evento, TipoNotificacao.PRAZO_INSCRICAO_FECHADO,
                "As inscrições de \"" + evento.getTitulo() + "\" encerraram. "
                    + confirmadas + " confirmados, " + aguardando + " aguardando pagamento.",
                "/eventos/" + evento.getId() + "/inscritos");
            evento.setAvisoPrazoFechadoEm(agora);
            eventoRepository.save(evento);
        }
    }

    private void notificarResponsaveis(Evento evento, TipoNotificacao tipo, String texto, String link) {
        for (EventoResponsavel r : evento.getResponsaveis()) {
            if (r.getPessoa() == null) continue;
            usuarioRepository.findByPessoaId(r.getPessoa().getId()).ifPresent(usuario ->
                notificacaoService.criar(tipo, evento.getIgreja().getId(), usuario.getId(), texto, link));
        }
    }
}
