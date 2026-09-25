package com.domus.api.modules.admin;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
public class AdminJwtService {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms:86400000}")
    private long expirationMs;

    public String generateToken(UsuarioDomusAdmin admin) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("domus-admin-api")
                    .withSubject(admin.getId().toString())
                    .withClaim("email", admin.getEmail())
                    .withClaim("role", "ROLE_DOMUS_ADMIN")
                    .withExpiresAt(getExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException e) {
            log.error("Erro ao gerar token JWT de admin. email={}", admin.getEmail(), e);
            throw new RuntimeException("Erro ao gerar token JWT de admin", e);
        }
    }

    public DecodedJWT validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("domus-admin-api")
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            log.warn("Token JWT de admin inválido ou expirado");
            return null;
        }
    }

    private Instant getExpirationDate() {
        return LocalDateTime.now()
                .plusSeconds(expirationMs / 1000)
                .toInstant(ZoneOffset.of("-03:00"));
    }
}
