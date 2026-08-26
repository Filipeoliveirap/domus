package com.domus.api.modules.evento.convite.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EntrarConviteRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @NotBlank(message = "O telefone é obrigatório.")
        @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter 10 ou 11 dígitos numéricos.")
        String telefone,
        /** Opcional em evento gratuito, obrigatório em evento pago (checado em
         *  {@code InscricaoService.inscreverConvidado}) — usado pra mandar o comprovante
         *  de pagamento. */
        @Email(message = "E-mail inválido.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String email,
        @Valid
        List<RespostaRequest> respostas
) {}
