package com.domus.api.config;

import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.domus.api.shared.security.Perfil;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final com.domus.api.shared.security.RateLimitFilter rateLimitFilter;

    private static final String ADMIN = Perfil.ADMIN_IGREJA.name();
    private static final String LIDER = Perfil.LIDER.name();
    private static final String COMUM = Perfil.ACESSO_COMUM.name();

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                // Com o token em cookie o navegador anexa-o em toda requisição, inclusive
                // cross-site — o que o header Authorization impedia sem custo. A defesa é
                // dupla: SameSite=Lax + CSRF double-submit.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                "/auth/login",
                                "/auth/google/login",
                                "/auth/google/registrar",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/igrejas/registrar"))
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

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
                        .requestMatchers("/igrejas/minha").hasRole(ADMIN)
                        // "/*" casaria com "/minha" se viesse antes — Spring Security
                        // avalia matchers em ordem.
                        .requestMatchers(HttpMethod.GET, "/igrejas/*").authenticated()

                        .requestMatchers("/usuarios/**")
                        .hasRole(ADMIN)

                        .requestMatchers(HttpMethod.GET, "/pessoas/bairros")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        // Self-service: editar a própria pessoa é de todo perfil;
                        // editar terceiro tem guarda fina no controller.
                        .requestMatchers(HttpMethod.PUT, "/pessoas/me")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.PUT, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/busca/usuarios").hasAnyRole(ADMIN, LIDER, COMUM)

                        // Matchers específicos ANTES dos curingas /eventos/** —
                        // Spring Security casa o primeiro que der match.
                        .requestMatchers(HttpMethod.POST, "/eventos/*/presenca/marcar-todos")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.PATCH, "/eventos/*/presenca/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.GET, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers("/eventos/*/inscricoes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/inscricoes/**", "/acompanhantes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.GET, "/eventos/*/elegibilidade")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/eventos/*/relatorio")
                        .hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/eventos/relatorio-geral")
                        .hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/eventos/tipos")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.GET, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.PUT, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.DELETE, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/locais-evento").authenticated()
                        .requestMatchers("/locais-evento/**").hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/igrejas-vinculadas")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers("/igrejas-vinculadas/**").hasRole(ADMIN)

                        // Acesso amplo — a verificação fina (Permissoes + capacidadesExtras)
                        // fica no controller.
                        .requestMatchers(
                                "/movimentacoes/**",
                                "/categorias/**",
                                "/relatorios/**",
                                "/dashboard",
                                "/admin/**"
                        ).hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/busca/movimentacoes").hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/busca/categorias").hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.GET, "/fotos/*")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/fotos")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers
                        // HSTS: request.isSecure() é sempre false no Spring porque o tráfego
                        // chega via proxy HTTP interno. Depende de server.forward-headers-strategy=framework.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(cto -> {})
                        .referrerPolicy(rp -> rp.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'")))
                .exceptionHandling(ex -> ex
                        // 401 vs 403 explícitos: sem isso o front trata 401 como "sessão
                        // morreu" e desloga o usuário em erros de autorização.
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) ->
                                res.setStatus(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                // Rate limiting roda DEPOIS do CsrfFilter (flood sem token CSRF leva 403
                // barato) e ANTES da autenticação. A ordem destas duas linhas importa:
                // addFilterBefore(_, SecurityFilter.class) só resolve porque a linha acima
                // já registrou a ordem do SecurityFilter.
                .addFilterBefore(rateLimitFilter, SecurityFilter.class)
                .build();
    }

    // Secure forçado: request.isSecure() é sempre false aqui (proxy interno HTTP),
    // e herdar do request deixaria o XSRF-TOKEN sem Secure enquanto os cookies
    // de sessão têm — abertura para cookie-tossing.
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite("Lax"));
        return repository;
    }

    // setCsrfRequestAttributeName(null) desliga o carregamento adiado (padrão do
    // Spring Security 6): sem isso o token é resolvido tarde demais e o cookie
    // nunca é escrito — o front não teria o valor para devolver no header.
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ATENÇÃO: com sessão em cookie o navegador anexa o domus_access sozinho —
    // qualquer origem nesta lista faz chamadas plenamente autenticadas. O tráfego
    // do app é same-origin (proxy do Next) e não depende disto.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
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
