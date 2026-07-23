package com.domus.api.modules.evento.elegibilidade;

/** Códigos em um lugar só: o front decide por código, nunca por texto de mensagem. */
public final class CodigoImpedimento {
    private CodigoImpedimento() {}
    public static final String FAIXA_ETARIA          = "FAIXA_ETARIA";
    public static final String SEM_DATA_NASCIMENTO   = "SEM_DATA_NASCIMENTO";
    public static final String EXCLUSIVO_MEMBROS     = "EXCLUSIVO_MEMBROS";
    public static final String ESTADO_CIVIL          = "ESTADO_CIVIL";
    public static final String SEM_ESTADO_CIVIL      = "SEM_ESTADO_CIVIL";
    public static final String SEXO                  = "SEXO";
    public static final String SEM_SEXO              = "SEM_SEXO";
    public static final String VAGAS_ESGOTADAS       = "VAGAS_ESGOTADAS";
}
