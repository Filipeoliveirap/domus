package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CriarConvidadoRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @NotBlank(message = "O telefone é obrigatório.")
        @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter 10 ou 11 dígitos numéricos.")
        String telefone,
        /** Obrigatório em qualquer evento (2026-08-27) — usado pra mandar comprovante de
         *  pagamento em evento pago, e pra avisar se um evento gratuito virar pago depois
         *  (ver {@code EventoService.aplicarEventoVirouPago}). */
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String email,
        /** Preenchido só quando o admin selecionou um Visitante existente na busca (aba
         *  "Visitantes"); null pra "Pessoa de fora". */
        UUID visitanteId,
        @Valid
        List<RespostaRequest> respostas,
        /** Plano 4b — evento pago: false = quem está preenchendo paga agora; true = gera
         *  link pra pessoa pagar sozinha depois. */
        boolean gerarLink
) {}
