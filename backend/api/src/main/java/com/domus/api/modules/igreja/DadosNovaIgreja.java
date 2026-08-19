package com.domus.api.modules.igreja;

/** Usado pelos dois fluxos de cadastro: no nativo senhaHashOuNull vem preenchido, no Google é googleSubOuNull. */
public record DadosNovaIgreja(
        String nomeIgreja,
        String emailContato,
        String cnpj,
        String telefoneContato,
        String nomeAdmin,
        String emailAdmin,
        String senhaHashOuNull,
        String googleSubOuNull
) {}
