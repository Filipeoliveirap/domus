package com.domus.api.config.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheEvictor {

    private final StringRedisTemplate redisTemplate;

    public void evictPorIgreja(String nomeCache, UUID igrejaId) {
        try {
            String pattern = nomeCache + "::" + igrejaId + ":*";
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                List<String> keys = new ArrayList<>();
                cursor.forEachRemaining(keys::add);
                if (!keys.isEmpty()) redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao invalidar cache '{}'. igreja_id={}", nomeCache, igrejaId, ex);
        }
    }
}