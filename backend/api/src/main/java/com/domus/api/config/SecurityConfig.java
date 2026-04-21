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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configure(httpSecurity))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        //rotas públicas
                        .requestMatchers(
                                "/igrejas/registrar",
                                "/auth/login",
                                "/auth/forgot-password",
                                "/auth/reset-password"
                        ).permitAll()

                        //Usuários(somente ADMIN IGREJA)
                        .requestMatchers("/usuarios/**",
                                "auth/register")
                        .hasRole("ADMIN_IGREJA")

                        //Membros
                        .requestMatchers(HttpMethod.GET, "/membros/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
                        .requestMatchers(HttpMethod.POST, "/membros/**")
                        .hasRole("ADMIN_IGREJA")
                        .requestMatchers(HttpMethod.PUT, "/membros/**")
                        .hasRole("ADMIN_IGREJA").requestMatchers(HttpMethod.DELETE, "/membros/**")
                        .hasRole("ADMIN_IGREJA")

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
                        .anyRequest().authenticated()
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
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
}
