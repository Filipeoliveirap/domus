package com.domus.api.modules.pessoa.DTO;

import jakarta.validation.constraints.Size;

/**
 * Endereço no contrato da API — aninhado no membro. Tudo opcional; a validação de formato
 * fica no front (Zod). A única regra defensiva no back é o tamanho da UF.
 */
public record EnderecoDTO(
        @Size(max = 9) String cep,
        @Size(max = 255) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 255) String complemento,
        @Size(max = 255) String bairro,
        @Size(max = 255) String cidade,
        @Size(max = 2, message = "UF deve ter 2 letras") String uf
) {}
