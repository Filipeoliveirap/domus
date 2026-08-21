package com.domus.api.modules.evento.serie;

/** Só relevante quando {@link FrequenciaRecorrencia#MENSAL}. */
public enum TipoRecorrenciaMensal {
    /** Todo dia 15, por exemplo. */
    DIA_FIXO,
    /** Toda 1ª/2ª/3ª/última terça, por exemplo. */
    DIA_DA_SEMANA
}
