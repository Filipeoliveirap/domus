package com.domus.api.shared.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnderecoFormatterTest {

    @Test
    void enderecoNuloOuVazioViraNull() {
        assertThat(EnderecoFormatter.emLinhaUnica(null)).isNull();
        assertThat(EnderecoFormatter.emLinhaUnica(new Endereco())).isNull();
    }

    @Test
    void completoFormataTudo() {
        Endereco e = Endereco.builder()
                .cep("50000-000").logradouro("Rua das Flores").numero("123")
                .complemento("Sala 4").bairro("Centro").cidade("Recife").uf("PE").build();
        assertThat(EnderecoFormatter.emLinhaUnica(e))
                .isEqualTo("Rua das Flores, 123, Sala 4 - Centro, Recife/PE (50000-000)");
    }

    @Test
    void parcialOmiteOQueFalta() {
        Endereco e = Endereco.builder().logradouro("Praça da Matriz").cidade("Olinda").uf("PE").build();
        assertThat(EnderecoFormatter.emLinhaUnica(e)).isEqualTo("Praça da Matriz - Olinda/PE");
    }

    @Test
    void soCidade() {
        assertThat(EnderecoFormatter.emLinhaUnica(Endereco.builder().cidade("Recife").build()))
                .isEqualTo("Recife");
    }
}
