package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoRequest(
        @NotBlank(message = "O título é obrigatório.")
        @Size(max = 255, message = "O título deve ter no máximo 255 caracteres.")
        String titulo,
        @Size(max = 5000, message = "A descrição deve ter no máximo 5000 caracteres.")
        String descricao,
        @NotNull(message = "A data de início é obrigatória.")
        LocalDateTime inicioEm,
        LocalDateTime fimEm,

        /** Local cadastrado. Mutuamente exclusivo com {@code localTexto} — ver EventoService. */
        UUID localId,
        /** Local ad-hoc ("chácara do João"). Mutuamente exclusivo com {@code localId}. */
        @Size(max = 255, message = "O local deve ter no máximo 255 caracteres.")
        String localTexto,

        /** Texto livre com sugestões (ver GET /eventos/tipos). NÃO é a categoria financeira. */
        @Size(max = 80, message = "O tipo deve ter no máximo 80 caracteres.")
        String tipo,

        /** Pessoa responsável pelo evento; null = sem responsável definido. */
        UUID responsavelPessoaId,

        /** Nome do recorte (Kids, Jovens...). Alimenta selo e filtro; não valida nada. */
        @Size(max = 40, message = "O recorte etário deve ter no máximo 40 caracteres.")
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
        /** Só pode ser {@code true} quando {@code requerInscricao} também é. */
        Boolean controlaPresenca,

        Boolean restritoPropriaIgreja,

        /** Id da foto já enviada via {@code POST /fotos}; {@code null} = sem foto. */
        UUID fotoId,

        /** {@code null} = evento avulso. Preenchido = cria uma EventoSerie junto. */
        @jakarta.validation.Valid
        com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest recorrencia,

        /** Endereço estruturado ad-hoc — só deste evento. Exclusivo com {@code localId} e {@code localTexto}. */
        @jakarta.validation.Valid
        com.domus.api.modules.pessoa.DTO.EnderecoDTO enderecoLocal
) {}
