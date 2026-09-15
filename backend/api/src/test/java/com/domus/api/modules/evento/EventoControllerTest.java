package com.domus.api.modules.evento;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * eventos — cobre a ordem de `requestMatchers` em SecurityConfig (GET liberado pra todo
 * perfil; POST/PUT/DELETE restrito a ADMIN_IGREJA/LIDER) e a validação de `@Valid` no
 * cadastro/edição.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Evento " + UUID.randomUUID())
                .emailContato("evento-" + UUID.randomUUID() + "@teste.com")
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

    private Evento evento() {
        Evento e = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento Teste " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(10))
                .build());
        entityManager.flush();
        return e;
    }

    private String corpoMinimo() {
        return "{\"titulo\":\"Culto de Celebração\",\"inicioEm\":\"" + LocalDateTime.now().plusDays(5) + "\"}";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/eventos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/eventos"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void cadastrar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/eventos").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastrar_admin_criaCom201() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/eventos").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.titulo").value("Culto de Celebração"));
    }

    @Test
    void cadastrar_semTitulo_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/eventos").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"inicioEm\":\"" + LocalDateTime.now().plusDays(5) + "\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/eventos")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        Evento e = evento();

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{id}", e.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_lider_permitidoCom200() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        Evento e = evento();

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{id}", e.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        lider))
                .andExpect(status().isOk());
    }

    @Test
    void arquivar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        Evento e = evento();

        mockMvc.perform(auth.autenticado(delete("/eventos/{id}", e.getId()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_admin_permitidoCom204() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Evento e = evento();

        mockMvc.perform(auth.autenticado(delete("/eventos/{id}", e.getId()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void arquivados_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/eventos/arquivados"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivados_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/eventos/arquivados"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void elegibilidade_qualquerPerfilAutenticado_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        Evento e = evento();

        mockMvc.perform(auth.autenticado(get("/eventos/{id}/elegibilidade", e.getId()), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorId_isolamentoPorIgreja_recusaCom404() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        Evento eDeOutraIgreja = eventoRepository.save(Evento.builder()
                .igreja(outraIgreja).titulo("Evento de Outra Igreja")
                .inicioEm(LocalDateTime.now().plusDays(10))
                .build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/eventos/{id}", eDeOutraIgreja.getId()), admin))
                .andExpect(status().isNotFound());
    }
}
