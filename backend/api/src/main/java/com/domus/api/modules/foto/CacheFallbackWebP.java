package com.domus.api.modules.foto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Cache in-memory para conversões WebP → JPEG sob demanda (fallback para clientes
 * que não aceitam WebP). Limitado a 100 entradas e TTL de 5 minutos para evitar
 * consumo excessivo de memória.
 */
@Component
@Slf4j
public class CacheFallbackWebP {

    private static final int MAX_ENTRIES = 100;
    private static final long TTL_MS = 5 * 60 * 1000L; // 5 minutos

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Retorna os bytes do cache se disponíveis e não expirados.
     * Caso contrário, executa o supplier, cacheia e retorna.
     */
    public byte[] obter(String chave, Supplier<byte[]> supplier) {
        Entry entry = cache.get(chave);
        if (entry != null && !entry.expirou()) {
            log.debug("Cache hit: {}", chave);
            return entry.bytes;
        }

        log.debug("Cache miss: {}", chave);
        byte[] bytes = supplier.get();
        cache.put(chave, new Entry(bytes, System.currentTimeMillis()));
        limparExpirados();
        return bytes;
    }

    private void limparExpirados() {
        if (cache.size() > MAX_ENTRIES) {
            int removidos = 0;
            for (var it = cache.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue().expirou()) {
                    it.remove();
                    removidos++;
                }
            }
            if (removidos > 0) {
                log.debug("Cache: removidas {} entradas expiradas, restam {}", removidos, cache.size());
            }
        }
    }

    private record Entry(byte[] bytes, long criadoEm) {
        boolean expirou() {
            return System.currentTimeMillis() - criadoEm > TTL_MS;
        }
    }
}
