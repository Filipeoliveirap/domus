package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcompanhanteRequest(
        @NotBlank(message = "O nome do convidado é obrigatório.")
        @Size(max = 255)
        String nome,
        @Size(max = 20)
        String telefone,
        /** Só tem efeito em evento pago: {@code true} gera um link de pagamento
         *  compartilhável (prazo mais longo); {@code false} (padrão) cobra o valor
         *  para pagamento imediato, como se fosse o próprio titular pagando agora. */
        boolean gerarLinkPagamento
) {
    /** Compatibilidade com o formato anterior (sem escolha de cobrança) — usado por
     *  quem ainda não decide entre "pagar agora"/"gerar link" (evento gratuito, ou
     *  chamada direta em teste/código já existente). */
    public AcompanhanteRequest(String nome, String telefone) {
        this(nome, telefone, false);
    }
}
