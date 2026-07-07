package com.domus.api.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.domus.api.modules.usuario.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
public class TokenService {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms}")
    private long expirationMs;

    public String generateToken(Usuario user) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("domus-api")
                    .withSubject(user.getId().toString())
                    .withClaim("igreja_id", user.getIgreja().getId().toString())
                    .withExpiresAt(getExpirationDate())
                    .sign(algorithm);

        } catch (JWTCreationException e) {
            log.error("Erro ao gerar token JWT. email={}", user.getEmail(), e);
            throw new RuntimeException("Erro ao gerar token JWT", e);
        }

    }

    public String validateToken(String token) {
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("domus-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException e) {
            log.warn("Token JWT inválido ou expirado");
            return null;
        }
    }

    private Instant getExpirationDate() {
        return LocalDateTime.now()
                .plusSeconds(expirationMs / 1000)
                .toInstant(ZoneOffset.of("-03:00"));
    }
}
