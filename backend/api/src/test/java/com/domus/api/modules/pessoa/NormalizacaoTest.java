package com.domus.api.modules.pessoa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizacaoTest {

    @Test
    void tituloDeCaso_deixaPrimeiraLetraDeCadaPalavraMaiuscula() {
        assertEquals("Centro", PessoaService.normalizar("centro"));
        assertEquals("Centro", PessoaService.normalizar("CENTRO"));
        assertEquals("Bairro Centro", PessoaService.normalizar("BAIRRO CENTRO"));
        assertEquals("Vila Nova", PessoaService.normalizar("vila nova"));
    }

    @Test
    void tituloDeCaso_tiraEspacosDasPontasEColapsaOsDoMeio() {
        assertEquals("Centro", PessoaService.normalizar("  centro  "));
        assertEquals("Bairro Centro", PessoaService.normalizar("bairro   centro"));
    }

    @Test
    void tituloDeCaso_nuloOuVazioViraNulo() {
        assertNull(PessoaService.normalizar(null));
        assertNull(PessoaService.normalizar(""));
        assertNull(PessoaService.normalizar("   "));
    }
}
