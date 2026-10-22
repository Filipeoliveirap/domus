package com.domus.api.modules.igreja;

import java.math.BigDecimal;
import java.util.Set;

public enum PlanoAssinatura {
    BASICO("Básico", 50, 0, new BigDecimal("49.00"), Set.of(
        FeaturePlan.FEED_SOCIAL,
        FeaturePlan.CONTAS_A_PAGAR,
        FeaturePlan.CHECKOUT_EVENTO,
        FeaturePlan.RELATORIOS_AVANCADOS,
        FeaturePlan.CAMPOS_PERSONALIZADOS
    )),
    PRO("Pro", 200, 2, new BigDecimal("129.00"), Set.of(FeaturePlan.values())),
    PRO_PLUS("Pro+", 600, 5, new BigDecimal("249.00"), Set.of(FeaturePlan.values())),
    ENTERPRISE("Enterprise", 99999, 9999, new BigDecimal("449.00"), Set.of(FeaturePlan.values())),
    @Deprecated
    PREMIUM("Premium", 200, 2, new BigDecimal("129.00"), Set.of(FeaturePlan.values()));

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
