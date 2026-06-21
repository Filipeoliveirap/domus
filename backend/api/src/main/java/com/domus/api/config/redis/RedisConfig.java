package com.domus.api.config.redis;

import com.domus.api.modules.igreja.DTO.IgrejaDTO;
import com.domus.api.modules.usuario.DTO.PagedResponse;
import com.domus.api.modules.usuario.DTO.UsuarioResponseDTO;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

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
                .entryTtl(Duration.ofHours(5))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serUsuarios));

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();

        caches.put("igreja", igrejaConfig);
        caches.put("usuarios", usuariosConfig);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(caches)
                .build();
    }
}