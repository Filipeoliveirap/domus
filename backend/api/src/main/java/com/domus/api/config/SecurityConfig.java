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
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final com.domus.api.shared.security.RateLimitFilter rateLimitFilter;

    /**
     * Mesma property que rege os cookies de sessão (ver AuthCookieFactory): o XSRF-TOKEN
     * precisa acompanhar, senão fica o único cookie sem Secure.
     */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                // Com o token em cookie, o navegador o envia SOZINHO em toda requisição —
                // inclusive nas disparadas por outro site. É isso que abre CSRF e o que o
                // header Authorization impedia de graça. Defesa em duas camadas:
                // SameSite=Lax (o navegador não anexa o cookie em POST cross-site) e
                // double-submit (o site atacante faz o cookie ser enviado, mas a Same-Origin
                // Policy o impede de LER o valor para repetir no header).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        // Rotas públicas: rodam sem sessão, então não há cookie para um
                        // atacante cavalgar. Resíduo aceito: login CSRF (ver BACKLOG);
                        // o SameSite=Lax já o barra na prática.
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
                        // HSTS: só é escrito quando request.isSecure() — e o Spring recebe o
                        // salto interno do proxy em HTTP puro. Por isso depende de
                        // server.forward-headers-strategy=framework, que faz o Spring confiar
                        // no X-Forwarded-Proto do proxy. Sem isso este bloco seria letra morta.
                        // A CAMADA QUE REALMENTE PROTEGE é o HSTS do front (next.config.ts),
                        // que cobre a origem inteira; este aqui é defesa em profundidade para
                        // quem alcançar a API direto.
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
                // Rate limiting roda antes da AUTENTICAÇÃO (barra floods anônimos barato),
                // mas depois do CsrfFilter: um flood de POST sem X-XSRF-TOKEN leva 403 e não
                // chega a ser contado. Respostas são baratas, então fica assim (ver BACKLOG).
                // A ORDEM DESTAS DUAS LINHAS IMPORTA: addFilterBefore(_, SecurityFilter.class)
                // só resolve porque a linha acima já registrou a ordem do SecurityFilter.
                // Invertê-las quebra o boot com "does not have a registered order".
                .addFilterBefore(rateLimitFilter, SecurityFilter.class)
                .build();
    }

    /**
     * Repositório do token CSRF.
     *
     * <p>O {@code Secure} é forçado pela nossa própria config em vez de herdado. O padrão do
     * {@code CookieCsrfTokenRepository} é {@code request.isSecure()} — que aqui é SEMPRE
     * false, porque o Spring só recebe o salto HTTP interno vindo do proxy do Next, nunca a
     * conexão TLS do navegador. Herdar deixaria o XSRF-TOKEN sem {@code Secure} enquanto os
     * cookies de sessão têm, abrindo cookie-tossing: um atacante de rede sobrescreve o
     * XSRF-TOKEN por HTTP com um valor conhecido e casa com o header.
     */
    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite("Lax"));
        return repository;
    }

    /**
     * O XSRF-TOKEN é deliberadamente legível por JS — e isso não contradiz o httpOnly dos
     * cookies de sessão. Ele NÃO é credencial: não prova quem você é, só prova que quem
     * montou a requisição enxerga a mesma origem. Um XSS lendo-o não ganha nada, porque XSS
     * já roda dentro da origem. httpOnly defende do script injetado DENTRO da página; CSRF
     * defende do site DE FORA.
     *
     * <p>setCsrfRequestAttributeName(null) desliga o carregamento adiado (padrão do Spring
     * Security 6). Sem isso o token é resolvido tarde demais e o cookie não é escrito nas
     * respostas — o front nunca teria o valor para devolver no header.
     */
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

    /**
     * ATENÇÃO: com a sessão em cookie, esta lista virou um interruptor de comprometimento.
     *
     * <p>Antes, o token ia no header — uma origem liberada aqui só conseguia ler respostas se
     * JÁ tivesse o token. Agora o navegador anexa o {@code domus_access} sozinho, então
     * qualquer origem nesta lista faz chamadas plenamente autenticadas e lê o resultado.
     * O tráfego do app é same-origin (proxy do Next) e não depende disto — mas o raio de
     * explosão de um valor errado em {@code CORS_ALLOWED_ORIGINS} aumentou muito.
     */
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
