package com.domus.api.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class
SecurityConfig {

    private final SecurityFilter securityFilter;
    private final com.domus.api.shared.security.RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        //rotas públicas
                        .requestMatchers(
                                "/igrejas/registrar",
                                "/auth/login",
                                "/auth/google/login",
                                "/auth/google/registrar",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/forgot-password",
                                "/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/igrejas/*").permitAll()

                        //Usuários(somente ADMIN IGREJA)
                        .requestMatchers("/usuarios/**")
                        .hasRole("ADMIN_IGREJA")

                        //Membros
                        .requestMatchers(HttpMethod.GET, "/membros/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
                        .requestMatchers(HttpMethod.POST, "/membros/**")
                        .hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.PUT, "/membros/**")
                        .hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.DELETE, "/membros/**")
                        .hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.GET, "/busca/usuarios").hasRole("ADMIN_IGREJA")

                        //Eventos
                        .requestMatchers(HttpMethod.GET, "/eventos/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
                        .requestMatchers(HttpMethod.POST, "/eventos/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER")
                        .requestMatchers(HttpMethod.PUT, "/eventos/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER")
                        .requestMatchers(HttpMethod.DELETE, "/eventos/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER")

                        //Financeiro(somente ADMIN IGREJA)
                        .requestMatchers(
                                "/movimentacoes/**",
                                "/categorias/**",
                                "/relatorios/**"
                        ).hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.GET, "/busca/movimentacoes").hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.GET, "/busca/categorias").hasRole("ADMIN_IGREJA")
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        // HSTS: força HTTPS (só aplicado em requisições seguras; inofensivo em dev http).
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Clickjacking: a API nunca deve ser embutida em iframe.
                        .frameOptions(frame -> frame.deny())
                        // Não deixar o navegador "adivinhar" o content-type.
                        .contentTypeOptions(cto -> {})
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // API só devolve JSON: nada deve ser carregado a partir dela.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limiting roda ANTES da autenticação: barra floods anônimos barato.
                .addFilterBefore(rateLimitFilter, SecurityFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        // Origens vindas de env (separadas por vírgula): localhost em dev, domínio real em prod.
        config.setAllowedOrigins(List.of(allowedOrigins.split("\\s*,\\s*")));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }


}
