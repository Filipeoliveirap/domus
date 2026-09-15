package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AutenticacaoTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * igreja. `SecurityConfig` tem dois comentários avisando sobre a mesma classe de bug
 * (matcher exato "/igrejas/minha" não cobre subcaminhos como "/minha/logo" — cada um
 * precisa do próprio matcher, senão cai em `anyRequest().authenticated()` e qualquer
 * perfil logado mexe em configuração da igreja inteira). Escrever este teste achou um
 * terceiro caso da mesma classe que não tinha matcher: `PUT /igrejas/minha/rotulos`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IgrejaControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste " + UUID.randomUUID())
                .emailContato("igreja-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();
    }

    private Usuario usuarioComRole(String nomeRole) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Login Teste " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome(nomeRole).orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
        entityManager.flush();
        return usuario;
    }

    @Test
    void buscarMinhaIgreja_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/igrejas/minha"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscarMinhaIgreja_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/igrejas/minha"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarMinhaIgreja_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/igrejas/minha"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarMinhaIgreja_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Nova\",\"emailContato\":\"nova@teste.com\"}"),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizarMinhaIgreja_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Nova\",\"emailContato\":\"nova@teste.com\"}"),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarMinhaIgreja_semTitulo_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"emailContato\":\"nova@teste.com\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void atualizarMinhaIgreja_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(put("/igrejas/minha")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nova\",\"emailContato\":\"nova@teste.com\"}"))
                .andExpect(status().isForbidden());
    }

    /** Achado escrevendo este teste: sem matcher próprio, esta rota caía em
     *  `anyRequest().authenticated()` — qualquer perfil logado renomeava os módulos da
     *  igreja inteira ("Ministério"/"Célula"/"Congregação"). Corrigido em SecurityConfig. */
    @Test
    void atualizarRotulos_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha/rotulos").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizarRotulos_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha/rotulos").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizarRotulos_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/igrejas/minha/rotulos").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void atualizarLogo_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        patch("/igrejas/minha/logo").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fotoId\":null}"),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizarLogo_admin_permitidoCom204() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        patch("/igrejas/minha/logo").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fotoId\":null}"),
                        admin))
                .andExpect(status().isNoContent());
    }
}
