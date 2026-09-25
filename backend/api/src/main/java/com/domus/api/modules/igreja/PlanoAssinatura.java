package com.domus.api.modules.igreja;

import java.math.BigDecimal;
import java.util.Set;

public enum PlanoAssinatura {
    BASICO("Básico", 60, 0, new BigDecimal("79.00"), Set.of()),
    PRO("Pro", 300, 3, new BigDecimal("179.00"), Set.of(FeaturePlan.values())),
    PRO_PLUS("Pro+", 800, 5, new BigDecimal("299.00"), Set.of(FeaturePlan.values())),
    ENTERPRISE("Enterprise", 99999, 9999, new BigDecimal("499.00"), Set.of(FeaturePlan.values()));

    private final String nomeExibicao;
    private final int limitePessoas;
    private final int limiteCongregacoes;
    private final BigDecimal valorMensal;
    private final Set<FeaturePlan> featuresHabilitadas;

    PlanoAssinatura(String nomeExibicao, int limitePessoas, int limiteCongregacoes, BigDecimal valorMensal, Set<FeaturePlan> featuresHabilitadas) {
        this.nomeExibicao = nomeExibicao;
        this.limitePessoas = limitePessoas;
        this.limiteCongregacoes = limiteCongregacoes;
        this.valorMensal = valorMensal;
        this.featuresHabilitadas = featuresHabilitadas;
    }

    public boolean temFeature(FeaturePlan feature) {
        return featuresHabilitadas.contains(feature);
    }

    public String getNomeExibicao() { return nomeExibicao; }
    public int getLimitePessoas() { return limitePessoas; }
    public int getLimiteCongregacoes() { return limiteCongregacoes; }
    public BigDecimal getValorMensal() { return valorMensal; }
    public Set<FeaturePlan> getFeaturesHabilitadas() { return featuresHabilitadas; }
}
