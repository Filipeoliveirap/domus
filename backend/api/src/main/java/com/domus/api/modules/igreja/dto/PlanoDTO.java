package com.domus.api.modules.igreja.dto;

import com.domus.api.modules.igreja.FeaturePlan;
import com.domus.api.modules.igreja.PlanoAssinatura;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record PlanoDTO(
    String id,
    String nomeExibicao,
    int limitePessoas,
    int limiteCongregacoes,
    BigDecimal valorMensal,
    Set<String> featuresHabilitadas,
    List<String> descricoesFeaturesHabilitadas
) {
    public static PlanoDTO de(PlanoAssinatura plano) {
        Set<String> features = plano.getFeaturesHabilitadas().stream()
            .map(Enum::name)
            .collect(Collectors.toSet());

        List<String> descricoes = plano.getFeaturesHabilitadas().stream()
            .map(FeaturePlan::getDescricao)
            .collect(Collectors.toList());

        return new PlanoDTO(
            plano.name(),
            plano.getNomeExibicao(),
            plano.getLimitePessoas(),
            plano.getLimiteCongregacoes(),
            plano.getValorMensal(),
            features,
            descricoes
        );
    }
}
