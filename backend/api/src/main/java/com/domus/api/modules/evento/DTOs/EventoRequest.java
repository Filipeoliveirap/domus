package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoRequest(
        @NotBlank(message = "O título é obrigatório.")
        String titulo,
        String descricao,
        @NotNull(message = "A data de início é obrigatória.")
        LocalDateTime inicioEm,
        LocalDateTime fimEm,

        /** Local cadastrado. Mutuamente exclusivo com {@code localTexto} — ver EventoService. */
        UUID localId,
        /** Local ad-hoc ("chácara do João"). Mutuamente exclusivo com {@code localId}. */
        String localTexto,

        /** Texto livre com sugestões (ver GET /eventos/tipos). NÃO é a categoria financeira. */
        String tipo,

        /** Pessoa responsável pelo evento; null = sem responsável definido. */
        UUID responsavelPessoaId,

        /** Nome do recorte (Kids, Jovens...). Alimenta selo e filtro; não valida nada. */
        String recorteEtario,
        @PositiveOrZero(message = "A idade mínima não pode ser negativa.")
        Integer idadeMin,
        @PositiveOrZero(message = "A idade máxima não pode ser negativa.")
        Integer idadeMax,
        EstadoCivil restricaoEstadoCivil,
        Sexo restricaoSexo,

        @Positive(message = "As vagas devem ser maiores que zero.")
        Integer vagas,
        @Positive(message = "O valor deve ser maior que zero.")
        java.math.BigDecimal preco,
        Boolean exclusivoMembros,
        Boolean requerInscricao,
        /**
         * Só pode ser {@code true} quando {@code requerInscricao} também é — ver
         * {@link com.domus.api.modules.evento.EventoService#validarControlaPresenca}.
         */
        Boolean controlaPresenca,

        Boolean restritoPropriaIgreja,

        /** Id da foto já enviada via {@code POST /fotos}; {@code null} = sem foto. */
        UUID fotoId
) {}
