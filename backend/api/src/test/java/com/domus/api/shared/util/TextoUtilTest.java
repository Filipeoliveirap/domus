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

    @Test
    void prefixarSemDuplicar_nomeSemPrefixo_antepoe() {
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", "Comunidade da Graça"))
                .isEqualTo("igreja Comunidade da Graça");
        assertThat(TextoUtil.prefixarSemDuplicar("célula", "Jovens")).isEqualTo("célula Jovens");
    }

    @Test
    void prefixarSemDuplicar_nomeJaComecaComPrefixo_naoDuplica() {
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", "Igreja Batista Central em Pitombeira"))
                .isEqualTo("Igreja Batista Central em Pitombeira");
        assertThat(TextoUtil.prefixarSemDuplicar("célula", "Célula Jovens")).isEqualTo("Célula Jovens");
    }

    @Test
    void prefixarSemDuplicar_ignoraAcentoECaixa() {
        assertThat(TextoUtil.prefixarSemDuplicar("célula", "CELULA Alfa")).isEqualTo("CELULA Alfa");
        assertThat(TextoUtil.prefixarSemDuplicar("Rede", "rede de Louvor")).isEqualTo("rede de Louvor");
    }

    @Test
    void prefixarSemDuplicar_prefixoIgualAoNomeInteiro_naoDuplica() {
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", "Igreja")).isEqualTo("Igreja");
    }

    @Test
    void prefixarSemDuplicar_prefixoApenasComoInicioDeOutraPalavra_antepoe() {
        // "igrejinha" começa com "igreja" mas não é a palavra "igreja" — não deve engolir o prefixo.
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", "Igrejinha do Bairro"))
                .isEqualTo("igreja Igrejinha do Bairro");
    }

    @Test
    void prefixarSemDuplicar_nomeNuloOuVazio_devolveSoOPrefixo() {
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", null)).isEqualTo("igreja");
        assertThat(TextoUtil.prefixarSemDuplicar("igreja", "  ")).isEqualTo("igreja");
    }
}
