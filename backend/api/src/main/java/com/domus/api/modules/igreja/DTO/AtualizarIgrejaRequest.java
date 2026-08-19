package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Só nome e e-mail são obrigatórios ({@code NOT NULL} desde V1); o resto é opcional pra completar o cadastro aos poucos. */
public record AtualizarIgrejaRequest(
        @NotBlank(message = "O nome da igreja é obrigatório.")
        @Size(max = 255) String nome,

        @Size(max = 255) String razaoSocial,
        @Size(max = 18) String cnpj,
        @Size(max = 255) String denominacao,

        @Size(max = 20) String sigla,

        @NotBlank(message = "O e-mail de contato é obrigatório.")
        @Email(message = "E-mail de contato inválido.")
        @Size(max = 255) String emailContato,

        @Size(max = 50) String telefoneContato,

        @Valid EnderecoDTO endereco,

        /** Id da foto (logo) já enviada via {@code POST /fotos}; {@code null} = sem logo. */
        UUID logoFotoId
) {}
