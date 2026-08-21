package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AutenticacaoTestSupport;
import com.domus.api.shared.security.Perfil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * As rotas públicas de auth (login, Google, forgot/reset-password, registrar igreja) saíram do
 * ignoringRequestMatchers do CSRF (2026-08-20) — mitigação de login CSRF. Este teste prova que
 * a camada de CSRF agora barra POST sem token nessas rotas, e que um token válido passa dela
 * (chegando na lógica de negócio, que aí sim recusa por credencial inválida).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthCsrfConfigTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    /**
     * O RateLimitFilter roda antes do CsrfFilter (2026-08-20) — requisição 403 de CSRF já
     * conta no limite de auth (10/min). Sem isso, execuções repetidas deste teste (ou de
     * outro que bata nas mesmas rotas) na mesma janela de 60s acumulam no Redis real
     * (compartilhado, sem Testcontainers) e um 429 legítimo mascara o que o teste quer provar.
     */
    @BeforeEach
    void limpaContadorDeRateLimit() {
        long minuto = Instant.now().getEpochSecond() / 60;
        Set<String> chaves = Set.of(
                "rl:auth:127.0.0.1:" + minuto,
                "rl:global:127.0.0.1:" + minuto);
        redisTemplate.delete(chaves);
    }

    @Test
    void loginSemTokenCsrfE403() throws Exception {
        // codigo=CSRF_INVALIDO é o que o front usa pra saber que vale a pena buscar um
        // token novo e tentar de nova, em vez de mostrar erro de permissão pro usuário.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALIDO"));
    }

    @Test
    void loginComTokenCsrfValidoPassaDaCamadaDeCsrf() throws Exception {
        // Credencial inexistente: o que importa aqui é NÃO ser 403 (CSRF) — a resposta de
        // negócio (401/400) prova que a requisição atravessou o CsrfFilter.
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@x.com\",\"senha\":\"qualquer\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("esperava passar da camada de CSRF, mas levou 403");
                    }
                });
    }

    @Test
    void forgotPasswordSemTokenCsrfE403() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarIgrejaSemTokenCsrfE403() throws Exception {
        mockMvc.perform(post("/igrejas/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /** Negação de role em requestMatchers (ex.: /usuarios/** exige ADMIN_IGREJA) passa pelo
     *  MESMO accessDeniedHandler que a falha de CSRF — sem essa distinção o front poderia
     *  achar que dá pra tentar de novo um 403 que na verdade é "você não pode fazer isso". */
    @Test
    void negacaoDeRoleE403ComCodigoAcessoNegadoNaoCsrf() throws Exception {
        AutenticacaoTestSupport auth = new AutenticacaoTestSupport(tokenService);
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste CSRF " + UUID.randomUUID())
                .emailContato("csrf-" + UUID.randomUUID() + "@teste.com")
                .build());
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Login Teste " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome(Perfil.LIDER.name()).orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
        entityManager.flush();

        mockMvc.perform(auth.autenticado(get("/usuarios"), usuario))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACESSO_NEGADO"));
    }
}
