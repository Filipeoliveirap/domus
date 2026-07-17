package com.domus.api.config.redis;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.financeiro.categoria.DTOs.CategoriaResponse;
import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoResponse;
import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.membro.DTO.MembroResponse;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import com.domus.api.shared.DTO.PagedResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        JavaType tipoUsuarios = mapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, UsuarioResponseDTO.class);

        Jackson2JsonRedisSerializer<Object> serUsuarios =
                new Jackson2JsonRedisSerializer<>(mapper, tipoUsuarios);


        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues();

        RedisCacheConfiguration igrejaConfig = base
                .entryTtl(Duration.ofHours(1))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, IgrejaDTO.class)));

        RedisCacheConfiguration usuariosConfig = base
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serUsuarios));

        JavaType tipoMembros = mapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, MembroResponse.class);
        Jackson2JsonRedisSerializer<Object> serMembros =
                new Jackson2JsonRedisSerializer<>(mapper, tipoMembros);

        RedisCacheConfiguration membrosConfig = base
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serMembros));

        JavaType tipoEventos = mapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, EventoResponse.class);
        Jackson2JsonRedisSerializer<Object> serEventos =
                new Jackson2JsonRedisSerializer<>(mapper, tipoEventos);

        RedisCacheConfiguration eventosConfig = base
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serEventos));

        JavaType tipoCategorias = mapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, CategoriaResponse.class);
        Jackson2JsonRedisSerializer<Object> serCategorias =
                new Jackson2JsonRedisSerializer<>(mapper, tipoCategorias);

        RedisCacheConfiguration categoriasConfig = base
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serCategorias));

        JavaType tipoMovimentacoes = mapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, MovimentacaoResponse.class);
        Jackson2JsonRedisSerializer<Object> serMovimentacoes =
                new Jackson2JsonRedisSerializer<>(mapper, tipoMovimentacoes);

        RedisCacheConfiguration movimentacoesConfig = base
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serMovimentacoes));

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();

        caches.put("igreja", igrejaConfig);
        caches.put("usuarios", usuariosConfig);
        caches.put("membros", membrosConfig);
        caches.put("eventos", eventosConfig);
        caches.put("categorias", categoriasConfig);
        caches.put("movimentacoes", movimentacoesConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(caches)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            private static final Logger log = LoggerFactory.getLogger("CacheErrorHandler");

            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Falha ao LER do cache '{}' (key={}). Seguindo para o banco.",
                        cache.getName(), key, ex);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Falha ao GRAVAR no cache '{}' (key={}).", cache.getName(), key, ex);
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Falha ao INVALIDAR cache '{}' (key={}).", cache.getName(), key, ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Falha ao LIMPAR cache '{}'.", cache.getName(), ex);
            }
        };
    }
}