package com.domus.api.modules.igreja.familia.consolidado;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Projeções das três consultas agregadas do consolidado.
 *
 * <p>Cada uma faz {@code GROUP BY igreja.id} sobre todas as igrejas da família de uma vez.
 * A alternativa — um laço com uma consulta por congregação — daria mais código e N vezes
 * mais idas ao banco.
 */
public interface ConsolidadoProjections {

    interface MembrosPorIgreja {
        UUID getIgrejaId();
        String getVinculo();
        Long getTotal();
    }

    interface EventosPorIgreja {
        UUID getIgrejaId();
        Long getRealizados();
        Long getProximos();
    }

    interface FinanceiroPorIgreja {
        UUID getIgrejaId();
        BigDecimal getEntradas();
        BigDecimal getSaidas();
    }
}
