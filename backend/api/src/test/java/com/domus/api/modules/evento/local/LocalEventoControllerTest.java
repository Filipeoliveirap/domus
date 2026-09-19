package com.domus.api.modules.evento.local;

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
 * locais de evento. Toda a restrição mora só em `SecurityConfig` (o controller não tem
 * `exigirAdmin`/capacidade nenhuma): `GET /locais-evento` é liberado a qualquer perfil
 * autenticado, mas `/locais-evento/**` (criar/editar/arquivar/arquivados) exige
 * ADMIN/LIDER — inclusive o próprio `GET /locais-evento/arquivados`, que cai no matcher
 * de wildcard, não no de listagem simples.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocalEventoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired LocalEventoRepository localEventoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Local " + UUID.randomUUID())
                .emailContato("local-" + UUID.randomUUID() + "@teste.com")
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

    private LocalEvento local() {
        LocalEvento l = localEventoRepository.save(LocalEvento.builder()
                .igreja(igreja).nome("Salão Teste " + UUID.randomUUID()).build());
        entityManager.flush();
        return l;
    }

    private String corpoMinimo(String nome) {
        return "{\"nome\":\"" + nome + "\"}";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/locais-evento"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/locais-evento"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void criar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/locais-evento").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Salão Novo")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_lider_permitidoCom201() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        post("/locais-evento").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Salão Novo")),
                        lider))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Salão Novo"));
    }

    @Test
    void criar_semNome_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/locais-evento").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/locais-evento")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo("Salão Novo")))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_acessoComum_recusaCom403() throws Exception {
        LocalEvento l = local();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        put("/locais-evento/{id}", l.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editado")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_admin_permitidoCom200() throws Exception {
        LocalEvento l = local();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/locais-evento/{id}", l.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editado")),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void atualizar_isolamentoPorIgreja_recusaCom404() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        LocalEvento localDeOutra = localEventoRepository.save(LocalEvento.builder()
                .igreja(outraIgreja).nome("Local de Outra Igreja").build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/locais-evento/{id}", localDeOutra.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Hackeado")),
                        admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void arquivar_acessoComum_recusaCom403() throws Exception {
        LocalEvento l = local();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/locais-evento/{id}", l.getId()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_lider_permitidoCom204() throws Exception {
        LocalEvento l = local();
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(delete("/locais-evento/{id}", l.getId()), lider))
                .andExpect(status().isNoContent());
    }

    @Test
    void arquivados_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/locais-evento/arquivados"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivados_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/locais-evento/arquivados"), admin))
                .andExpect(status().isOk());
    }
}
