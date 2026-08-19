package com.domus.api.config.redis;

import com.domus.api.modules.pessoa.Vinculo;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public final class CacheKeys {
    private CacheKeys() {}

    public static String usuarios(UUID igrejaId, String q, Pageable pageable) {
        String bruto = (q == null ? "" : q) + "|"
                + pageable.getPageNumber() + "|"
                + pageable.getPageSize() + "|"
                + pageable.getSort().toString();
        return igrejaId + ":" + sha256Curto(bruto);
    }

    /** Toda dimensão que muda a resposta (sensiveis, vinculo) precisa entrar na chave, senão o Redis vaza a resposta de um perfil/filtro para outro. */
    public static String pessoas(UUID igrejaId, String q, Pageable pageable, boolean sensiveis, Vinculo vinculo) {
        String bruto = (q == null ? "" : q) + "|"
                + pageable.getPageNumber() + "|"
                + pageable.getPageSize() + "|"
                + pageable.getSort().toString() + "|"
                + (sensiveis ? "full" : "reduzido") + "|"
                + (vinculo == null ? "todos" : vinculo.name());
        return igrejaId + ":" + sha256Curto(bruto);
    }

    public static String eventos(UUID igrejaId, String q, String tipo, String recorteEtario,
                                  String role, Pageable pageable) {
        String bruto = (q == null ? "" : q) + "|"
                + (tipo == null ? "" : tipo) + "|"
                + (recorteEtario == null ? "" : recorteEtario) + "|"
                + (role == null ? "" : role) + "|"
                + pageable.getPageNumber() + "|"
                + pageable.getPageSize() + "|"
                + pageable.getSort().toString();
        return igrejaId + ":" + sha256Curto(bruto);
    }

    public static String categorias(UUID igrejaId, String q, Pageable pageable) {
        String bruto = (q == null ? "" : q) + "|"
                + pageable.getPageNumber() + "|"
                + pageable.getPageSize() + "|"
                + pageable.getSort().toString();
        return igrejaId + ":" + sha256Curto(bruto);
    }

    public static String movimentacoes(UUID igrejaId, Pageable pageable) {
        String bruto = pageable.getPageNumber() + "|"
                + pageable.getPageSize() + "|"
                + pageable.getSort().toString();
        return igrejaId + ":" + sha256Curto(bruto);
    }

    private static String sha256Curto(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i] & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
