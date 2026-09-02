package com.domus.api.shared.dominio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnderecoTest {

    @Test
    void vazioNaoEstaPreenchido() {
        assertThat(new Endereco().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().cep("  ").logradouro("").build().estaPreenchido()).isFalse();
    }

    @Test
    void cepLogradouroOuCidadeSozinhosContam() {
        assertThat(Endereco.builder().cep("50000-000").build().estaPreenchido()).isTrue();
        assertThat(Endereco.builder().logradouro("Rua X").build().estaPreenchido()).isTrue();
        assertThat(Endereco.builder().cidade("Recife").build().estaPreenchido()).isTrue();
    }

    @Test
    void complementoNumeroOuBairroSozinhosNaoContam() {
        assertThat(Endereco.builder().numero("123").build().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().complemento("Apto 2").build().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().bairro("Centro").build().estaPreenchido()).isFalse();
    }
}
