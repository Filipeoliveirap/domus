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
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.domus.api.shared.security.Perfil;
import com.domus.api.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final com.domus.api.shared.security.RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    private static final String ADMIN = Perfil.ADMIN_IGREJA.name();
    private static final String LIDER = Perfil.LIDER.name();
    private static final String COMUM = Perfil.ACESSO_COMUM.name();

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        // Convite público: quem abre o link nunca teve sessão nenhuma (não faz
                        // sentido exigir cookie XSRF-TOKEN de um estranho vindo do WhatsApp) —
                        // CSRF protege sessão autenticada de forjadura, e aqui não existe sessão.
                        // Webhook do Mercado Pago: chamado pelo próprio Mercado Pago, sem
                        // sessão nossa — a validação de autenticidade é feita à parte, pela
                        // assinatura HMAC do header x-signature (MercadoPagoAssinaturaValidator).
                        // Cobrança pública: pagador abre o link (WhatsApp/e-mail) sem nunca
                        // ter tido sessão — mesma lógica de /convites/**.
                        .ignoringRequestMatchers("/convites/**", "/pagamentos/mercadopago/webhook", "/cobrancas/**"))
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
                        .requestMatchers("/convites/**").permitAll()
                        .requestMatchers("/pagamentos/mercadopago/webhook").permitAll()
                        .requestMatchers("/cobrancas/**").permitAll()
                        .requestMatchers("/igrejas/minha").hasRole(ADMIN)
                        // Critical 3b (revisão final de branch): conectar/desconectar/consultar a
                        // conta de recebimento da igreja é decisão de admin — sem este matcher,
                        // caía em anyRequest().authenticated() e qualquer perfil (ACESSO_COMUM
                        // incluso) podia mexer na conta de pagamento da igreja inteira.
                        .requestMatchers("/pagamentos/conta/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/igrejas/*").authenticated()

                        .requestMatchers("/usuarios/**")
                        .hasRole(ADMIN)

                        .requestMatchers(HttpMethod.GET, "/pessoas/bairros")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.PUT, "/pessoas/me")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.PUT, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/busca/usuarios").hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.POST, "/eventos/*/presenca/marcar-todos")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.PATCH, "/eventos/*/presenca/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes/minha/convite")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers("/eventos/*/inscricoes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/inscricoes/**", "/acompanhantes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.GET, "/inscricoes/*/respostas")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.PUT, "/inscricoes/*/respostas")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.GET, "/eventos/*/elegibilidade")
                        .authenticated()

                        .requestMatchers(HttpMethod.GET, "/eventos/*/relatorio")
                        .hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/eventos/relatorio-geral")
                        .hasAnyRole(ADMIN, LIDER)

                        .requestMatchers(HttpMethod.GET, "/eventos/tipos")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        .requestMatchers(HttpMethod.GET, "/eventos/arquivados")
                        .hasAnyRole(ADMIN, LIDER)

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
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(this::responderAcessoNegado))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                // Antes do CsrfFilter: requisição barrada por CSRF (403) também conta no
                // limite — senão um flood sem token nunca incrementa o contador (ver BACKLOG).
                .addFilterBefore(rateLimitFilter, CsrfFilter.class)
                .build();
    }

    /** Chamado tanto pra falha de CSRF (CsrfFilter) quanto pra negação de role em
     *  requestMatchers (AuthorizationFilter) — os dois rodam antes do DispatcherServlet,
     *  então nunca chegam no GlobalExceptionHandler. Sem distinguir o tipo aqui, o front
     *  não tem como saber se vale a pena tentar de novo (token CSRF velho) ou se é
     *  negação de verdade (não teria sentido reenviar). */
    private void responderAcessoNegado(jakarta.servlet.http.HttpServletRequest req,
                                        jakarta.servlet.http.HttpServletResponse res,
                                        org.springframework.security.access.AccessDeniedException e) throws java.io.IOException {
        boolean ehFalhaDeCsrf = e instanceof org.springframework.security.web.csrf.CsrfException;
        String codigo = ehFalhaDeCsrf ? "CSRF_INVALIDO" : "ACESSO_NEGADO";
        String mensagem = ehFalhaDeCsrf
                ? "Token de segurança ausente ou expirado. Tente novamente."
                : "Você não tem permissão para acessar este recurso.";

        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType("application/json");
        objectMapper.writeValue(res.getWriter(), ErrorResponse.of(403, codigo, mensagem));
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite("Lax"));
        return repository;
    }

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
