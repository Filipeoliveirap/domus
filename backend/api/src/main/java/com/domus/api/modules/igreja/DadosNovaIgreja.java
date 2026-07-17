package com.domus.api.modules.igreja;

/**
 * Pacote de dados para criar uma igreja com seu admin inicial.
 *
 * Usado pelos dois fluxos de cadastro (nativo e Google). A diferença entre eles fica só
 * nos dois últimos campos: no nativo, senhaHashOuNull vem preenchido e googleSubOuNull é null;
 * no Google, o inverso.
 */
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
