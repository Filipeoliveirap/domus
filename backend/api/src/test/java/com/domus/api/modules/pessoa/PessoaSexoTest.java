package com.domus.api.modules.pessoa;

import com.domus.api.modules.pessoa.DTO.PessoaResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `sexo` é NULÁVEL de propósito: pessoas já cadastradas não têm valor, e o
 * cadastro tem que continuar válido sem ele. Serve só pra restringir
 * inscrição em evento (ver {@code RegraSexo}), não pra descrever identidade.
 */
class PessoaSexoTest {

    @Test
    void pessoaResponseFrom_semSexo_continuaValida() {
        Pessoa membro = Pessoa.builder().nome("Ana").sexo(null).build();

        PessoaResponse resp = PessoaResponse.from(membro);

        assertNull(resp.sexo());
    }

    @Test
    void pessoaResponseFrom_comSexo_devolveOValor() {
        Pessoa membro = Pessoa.builder().nome("João").sexo(Sexo.HOMEM).build();

        PessoaResponse resp = PessoaResponse.from(membro);

        assertEquals(Sexo.HOMEM, resp.sexo());
    }

    @Test
    void pessoaResponseFrom_comSexoMulher_devolveOValor() {
        Pessoa membro = Pessoa.builder().nome("Maria").sexo(Sexo.MULHER).build();

        PessoaResponse resp = PessoaResponse.from(membro);

        assertEquals(Sexo.MULHER, resp.sexo());
    }
}
