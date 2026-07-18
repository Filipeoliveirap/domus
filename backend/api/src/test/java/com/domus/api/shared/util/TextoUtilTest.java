package com.domus.api.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextoUtilTest {

    @Test
    void titleCaseComConectoresMinusculos() {
        assertThat(TextoUtil.capitalizar("joão da silva")).isEqualTo("João da Silva");
        assertThat(TextoUtil.capitalizar("ofertas de missões")).isEqualTo("Ofertas de Missões");
    }

    @Test
    void normalizaCaixaEEspacos() {
        assertThat(TextoUtil.capitalizar("OFERTAS ESPECIAIS")).isEqualTo("Ofertas Especiais");
        assertThat(TextoUtil.capitalizar("  culto   da  família ")).isEqualTo("Culto da Família");
        assertThat(TextoUtil.capitalizar("dízimo")).isEqualTo("Dízimo");
    }

    @Test
    void conectorNaPrimeiraPalavraEhCapitalizado() {
        assertThat(TextoUtil.capitalizar("da vinci")).isEqualTo("Da Vinci");
    }

    @Test
    void nuloOuVazioViraNull() {
        assertThat(TextoUtil.capitalizar(null)).isNull();
        assertThat(TextoUtil.capitalizar("   ")).isNull();
    }
}
