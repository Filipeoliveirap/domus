package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record InscreverPessoasRequest(
        @NotEmpty(message = "Selecione ao menos um membro.")
        @Size(max = 500, message = "Selecione no máximo 500 pessoas por vez.")
        List<UUID> pessoaIds,

        /**
         * Task 14 (revisão pós-review) — subconjunto de {@code pessoaIds} escolhido como
         * "gerar link" na tela "Divisão de pagamento" do front (`EscolhaPagamentoPorPessoa`).
         * Nulo/vazio = ninguém vira link (comportamento anterior — todo mundo "paga
         * agora"). Só tem efeito em evento pago; ignorado em evento gratuito.
         */
        @Size(max = 500, message = "Selecione no máximo 500 pessoas por vez.")
        List<UUID> pessoasParaLink
) {}
