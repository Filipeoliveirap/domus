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
                        // ATENÇÃO: esta regra tem que vir ANTES do permitAll abaixo — a ordem
                        // manda no Spring Security, e "/igrejas/*" casaria com "/igrejas/minha",
                        // deixando os dados da igreja abertos sem autenticação.
                        .requestMatchers("/igrejas/minha").hasRole(ADMIN)
                        // Era permitAll e vazava nome/CNPJ/e-mail/telefone de QUALQUER igreja
                        // para quem nem está logado. Nenhuma tela consome este endpoint, e a
                        // feature de igrejas vinculadas passou a circular UUIDs de igrejas nas
                        // respostas — o que tornaria a enumeração muito mais útil para um curioso.
                        .requestMatchers(HttpMethod.GET, "/igrejas/*").authenticated()

                        //Usuários(somente ADMIN IGREJA)
                        .requestMatchers("/usuarios/**")
                        .hasRole(ADMIN)

                        //Pessoas
                        //Bairros ANTES do curinga: a lista de bairros é derivada do ENDEREÇO
                        //das pessoas, que é dado só de ADMIN. Deixá-la aberta entregaria pela
                        //porta lateral exatamente o que o DTO reduzido esconde.
                        .requestMatchers(HttpMethod.GET, "/pessoas/bairros")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/pessoas/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/pessoas/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/pessoas/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/pessoas/**")
                        .hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/busca/usuarios").hasRole(ADMIN)

                        //Inscrição em evento — DEVE vir ANTES dos curingas /eventos/**,
                        //senão o POST curinga (ADMIN+LÍDER) barra o ACESSO_COMUM, que é justamente
                        //quem se inscreve, e o GET curinga (todos) vaza a lista de inscritos.
                        //Mesma armadilha de ordenação já corrigida em /igrejas/*.
                        .requestMatchers(HttpMethod.GET, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers("/eventos/*/inscricoes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/inscricoes/**", "/acompanhantes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        //Elegibilidade — DEVE vir ANTES dos curingas /eventos/** (mesma armadilha
                        //de ordenação já mordida três vezes neste projeto: matcher específico
                        //sempre antes do curinga, "/**" casa zero segmentos e o curinga também
                        //casaria "/eventos/{id}/elegibilidade" se viesse antes).
                        .requestMatchers(HttpMethod.GET, "/eventos/*/elegibilidade")
                        .authenticated()

                        //Sugestões de tipo — DEVE vir ANTES do curinga /eventos/** (mesma
                        //armadilha de ordenação já mordida três vezes neste projeto).
                        .requestMatchers(HttpMethod.GET, "/eventos/tipos")
                        .hasAnyRole(ADMIN, LIDER, COMUM)

                        //Eventos
                        .requestMatchers(HttpMethod.GET, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.PUT, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.DELETE, "/eventos/**")
                        .hasAnyRole(ADMIN, LIDER)

                        // Locais de evento — GET (só leitura) é aberto a todo autenticado
                        // (a lista alimenta o formulário de evento, que qualquer perfil pode
                        // ver); escrever exige gestor. O específico do GET vem ANTES do
                        // curinga — regra que já mordeu este projeto três vezes.
                        .requestMatchers(HttpMethod.GET, "/locais-evento").authenticated()
                        .requestMatchers("/locais-evento/**").hasAnyRole(ADMIN, LIDER)

                        //Igrejas vinculadas (mãe/congregações) — expõe financeiro entre igrejas,
                        //então segue a mesma trava do financeiro: só ADMIN_IGREJA.
                        .requestMatchers("/igrejas-vinculadas/**").hasRole(ADMIN)

                        //Financeiro + Dashboard + Admin (somente ADMIN IGREJA)
                        .requestMatchers(
                                "/movimentacoes/**",
                                "/categorias/**",
                                "/relatorios/**",
                                "/dashboard",
                                "/admin/**"
                        ).hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/busca/movimentacoes").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/busca/categorias").hasRole(ADMIN)

                        //Fotos: qualquer perfil VÊ (avatar aparece em toda tela) e ENVIA —
                        //quem gerencia o que a foto ilustra (pessoa/evento) E quem troca a
                        //própria foto em Meu Perfil (todo perfil, inclusive ACESSO_COMUM).
                        //A restrição de QUAL pessoa/evento a foto pode vincular continua no
                        //backend (FotoService/PessoaService), isolada por igreja.
                        .requestMatchers(HttpMethod.GET, "/fotos/*")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/fotos")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
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
                        // 401 = NÃO SEI QUEM VOCÊ É (sem token, token expirado/inválido).
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // 403 = SEI QUEM VOCÊ É, MAS NÃO PODE. Precisa ser explícito, senão o
                        // Spring cai no entry point acima e devolve 401 nos dois casos — e o
                        // front, que trata 401 como "sessão morreu", renova o token, repete a
                        // requisição, leva 401 de novo e DESLOGA o usuário. Sintoma real
                        // (2026-07-21): membro e líder entravam e eram expulsos ao abrir
                        // qualquer tela cujo dado é de admin.
                        .accessDeniedHandler((req, res, e) ->
                                res.setStatus(HttpStatus.FORBIDDEN.value())))
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
