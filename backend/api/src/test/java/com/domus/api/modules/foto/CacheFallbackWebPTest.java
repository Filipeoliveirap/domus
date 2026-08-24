package com.domus.api.modules.foto;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CacheFallbackWebPTest {

    private final CacheFallbackWebP cache = new CacheFallbackWebP();

    @Test
    void retornaValorDoSupplierNaPrimeiraChamada() {
        byte[] resultado = cache.obter("chave-1", () -> new byte[]{1, 2, 3});
        assertThat(resultado).containsExactly(1, 2, 3);
    }

    @Test
    void retornaValorDoCacheEmChamadasSubsequentes() {
        AtomicInteger chamadasSupplier = new AtomicInteger(0);

        cache.obter("chave-2", () -> {
            chamadasSupplier.incrementAndGet();
            return new byte[]{1, 2, 3};
        });
        cache.obter("chave-2", () -> {
            chamadasSupplier.incrementAndGet();
            return new byte[]{4, 5, 6};
        });

        assertThat(chamadasSupplier.get()).isEqualTo(1);
    }
}
