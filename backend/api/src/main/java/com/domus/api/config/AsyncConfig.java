package com.domus.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool dedicado ao poll ativo de confirmação de pagamento ({@code PagamentoPollingService})
 * — nunca compartilha thread com o pool padrão do Spring, que não temos garantia de
 * dimensionamento. Cada tentativa de pagamento ocupa uma thread por até ~1 minuto (poll a
 * cada 3s), então o pool é pequeno de propósito: volume de pagamentos simultâneos do piloto
 * é baixo, e uma fila aqui só atrasa o poll — não perde o pagamento (o webhook, quando
 * chegar, confirma do mesmo jeito).
 */
@Configuration
public class AsyncConfig {

    @Bean("pagamentoPollingExecutor")
    public TaskExecutor pagamentoPollingExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pagamento-poll-");
        executor.initialize();
        return executor;
    }
}
