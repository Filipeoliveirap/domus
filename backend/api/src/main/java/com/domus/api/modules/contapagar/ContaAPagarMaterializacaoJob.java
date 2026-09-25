package com.domus.api.modules.contapagar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job diário que materializa ocorrências futuras de contas recorrentes.
 *  Só executa quando {@code domus.contapagar.materializacao.enabled=true} (default true). */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "domus.contapagar.materializacao.enabled", havingValue = "true", matchIfMissing = true)
public class ContaAPagarMaterializacaoJob {

    private final ContaAPagarService contaService;

    /** Executa todo dia às 01:05 da madrugada (hora do servidor). */
    @Scheduled(cron = "${domus.contapagar.materializacao.cron:0 5 1 * * *}")
    public void run() {
        log.info("Job de materialização de contas recorrentes iniciando.");
        long inicio = System.currentTimeMillis();
        try {
            contaService.materializarRecorrencias();
            long duracao = System.currentTimeMillis() - inicio;
            log.info("Job de materialização de contas recorrentes concluído em {}ms.", duracao);
        } catch (Exception e) {
            long duracao = System.currentTimeMillis() - inicio;
            log.error("Job de materialização de contas recorrentes falhou após {}ms.", duracao, e);
        }
    }
}
