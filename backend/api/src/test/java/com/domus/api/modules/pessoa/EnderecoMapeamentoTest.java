package com.domus.api.modules.pessoa;

import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import com.domus.api.shared.dominio.Endereco;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnderecoMapeamentoTest {

    @Test
    void membroResponseFrom_mapeiaOEnderecoAninhado() {
        Endereco endereco = Endereco.builder()
                .cep("01001000").logradouro("Praça da Sé").numero("100")
                .complemento("lado ímpar").bairro("Sé").cidade("São Paulo").uf("SP")
                .build();
        Pessoa membro = Pessoa.builder().nome("Ana").endereco(endereco).build();

        PessoaResponse resp = PessoaResponse.from(membro);

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
        Pessoa membro = Pessoa.builder().nome("Bia").endereco(null).build();

        PessoaResponse resp = PessoaResponse.from(membro);

        assertNull(resp.endereco());
    }
}
