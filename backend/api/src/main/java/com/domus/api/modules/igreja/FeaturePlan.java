package com.domus.api.modules.igreja;

public enum FeaturePlan {
    FEED_SOCIAL("Mural e Feed Social da Comunidade"),
    CONTAS_A_PAGAR("Gestão e Lembretes de Contas a Pagar"),
    CHECKOUT_EVENTO("Cobrança de Eventos Pagos via PIX/Cartão"),
    RELATORIOS_AVANCADOS("Relatórios Avançados e Balancete Anual"),
    CAMPOS_PERSONALIZADOS("Campos Personalizados em Eventos");

    private final String descricao;

    FeaturePlan(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
