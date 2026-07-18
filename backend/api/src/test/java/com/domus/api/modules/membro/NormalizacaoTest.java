package com.domus.api.modules.membro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NormalizacaoTest {

    @Test
    void tituloDeCaso_deixaPrimeiraLetraDeCadaPalavraMaiuscula() {
        assertEquals("Centro", MembroService.normalizar("centro"));
        assertEquals("Centro", MembroService.normalizar("CENTRO"));
        assertEquals("Bairro Centro", MembroService.normalizar("BAIRRO CENTRO"));
        assertEquals("Vila Nova", MembroService.normalizar("vila nova"));
    }

    @Test
    void tituloDeCaso_tiraEspacosDasPontasEColapsaOsDoMeio() {
        assertEquals("Centro", MembroService.normalizar("  centro  "));
        assertEquals("Bairro Centro", MembroService.normalizar("bairro   centro"));
    }

    @Test
    void tituloDeCaso_nuloOuVazioViraNulo() {
        assertNull(MembroService.normalizar(null));
        assertNull(MembroService.normalizar(""));
        assertNull(MembroService.normalizar("   "));
    }
}
