package com.domus.api.modules.membro;

import com.domus.api.modules.membro.DTO.MembroResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnderecoMapeamentoTest {

    @Test
    void membroResponseFrom_mapeiaOEnderecoAninhado() {
        Endereco endereco = Endereco.builder()
                .cep("01001000").logradouro("Praça da Sé").numero("100")
                .complemento("lado ímpar").bairro("Sé").cidade("São Paulo").uf("SP")
                .build();
        Membro membro = Membro.builder().nome("Ana").endereco(endereco).build();

        MembroResponse resp = MembroResponse.from(membro);

        assertNotNull(resp.endereco());
        assertEquals("01001000", resp.endereco().cep());
        assertEquals("Praça da Sé", resp.endereco().logradouro());
        assertEquals("100", resp.endereco().numero());
        assertEquals("Sé", resp.endereco().bairro());
        assertEquals("São Paulo", resp.endereco().cidade());
        assertEquals("SP", resp.endereco().uf());
    }

    @Test
    void membroResponseFrom_toleraEnderecoNulo() {
        Membro membro = Membro.builder().nome("Bia").endereco(null).build();

        MembroResponse resp = MembroResponse.from(membro);

        assertNull(resp.endereco());
    }
}
