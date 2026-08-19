package com.domus.api.modules.igreja.familia.consolidado;

import java.math.BigDecimal;
import java.util.UUID;

/** Cada consulta faz {@code GROUP BY igreja.id} sobre toda a família de uma vez, evitando N idas ao banco. */
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
