package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira cobrança de evento pago vencida, liberando a vaga, e cancela a inscrição
 * vinculada (que nasceu AGUARDANDO_PAGAMENTO — ninguém pagou a tempo). Roda a cada 5
 * minutos — suficiente dado que o prazo mínimo de cobrança é 30 minutos. */
@Component
public class CobrancaEventoExpiracaoJob {

    private final CobrancaEventoRepository repository;
    private final InscricaoRepository inscricaoRepository;

    public CobrancaEventoExpiracaoJob(CobrancaEventoRepository repository,
                                       InscricaoRepository inscricaoRepository) {
        this.repository = repository;
        this.inscricaoRepository = inscricaoRepository;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void executar() {
        var vencidas = repository.findByStatusAndExpiraEmBefore(StatusCobranca.PENDENTE, Instant.now());
        if (vencidas.isEmpty()) return;

        vencidas.forEach(CobrancaEvento::marcarComoExpirado);
        repository.saveAll(vencidas);

        var inscricaoIds = vencidas.stream().map(CobrancaEvento::getInscricaoId).toList();
        var inscricoes = inscricaoRepository.findAllById(inscricaoIds).stream()
                .filter(InscricaoEvento::estaAguardandoPagamento)
                .toList();
        if (inscricoes.isEmpty()) return;

        inscricoes.forEach(i -> i.setStatus(StatusInscricao.CANCELADA));
        inscricaoRepository.saveAll(inscricoes);
    }
}
