package com.domus.api.modules.usuario;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * usuários. Caso de interesse: `SecurityConfig` restringe `/usuarios/**` inteiro a
 * `hasRole(ADMIN)` — nenhum método, verbo ou capacidade extra abre exceção aqui (diferente
 * de `Pessoa`, onde a permissão real mora no controller). Então o valor deste teste é
 * provar que LIDER (o perfil mais próximo de admin) segue barrado em qualquer verbo, e que
 * ADMIN passa em todos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Usuario " + UUID.randomUUID())
                .emailContato("usuario-" + UUID.randomUUID() + "@teste.com")
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

    /** Pessoa com e-mail já cadastrado e sem usuário — pronta pra "conceder acesso". */
    private Pessoa pessoaSemAcesso() {
        Pessoa p = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Pessoa Sem Acesso " + UUID.randomUUID())
                .email("sem-acesso-" + UUID.randomUUID() + "@teste.com")
                .vinculo(Vinculo.MEMBRO)
                .build());
        entityManager.flush();
        return p;
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(get("/usuarios"), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/usuarios"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void concederAcesso_admin_criaCom201() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Pessoa pessoa = pessoaSemAcesso();

        mockMvc.perform(auth.autenticado(
                        post("/usuarios/conceder-acesso").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + pessoa.getId() + "\",\"role\":\"ACESSO_COMUM\"}"),
                        admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(pessoa.getEmail()))
                .andExpect(jsonPath("$.role").value("ACESSO_COMUM"));
    }

    @Test
    void concederAcesso_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        Pessoa pessoa = pessoaSemAcesso();

        mockMvc.perform(auth.autenticado(
                        post("/usuarios/conceder-acesso").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + pessoa.getId() + "\",\"role\":\"ACESSO_COMUM\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void concederAcesso_semPessoaId_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/usuarios/conceder-acesso").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ACESSO_COMUM\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concederAcesso_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Pessoa pessoa = pessoaSemAcesso();

        mockMvc.perform(post("/usuarios/conceder-acesso")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pessoaId\":\"" + pessoa.getId() + "\",\"role\":\"ACESSO_COMUM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarPorId_isolamentoPorIgreja_recusaCom404() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        Pessoa pessoaDeOutraIgreja = pessoaRepository.save(Pessoa.builder()
                .igreja(outraIgreja).nome("Pessoa de Outra Igreja").vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();
        Usuario usuarioDeOutraIgreja = usuarioRepository.save(Usuario.builder()
                .igreja(outraIgreja).pessoa(pessoaDeOutraIgreja).role(role).ativo(true).build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/usuarios/{id}", usuarioDeOutraIgreja.getId()), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRole_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        Usuario alvo = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        patch("/usuarios/{id}/role", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"LIDER\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Usuario alvo = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        patch("/usuarios/{id}/role", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"LIDER\"}"),
                        admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LIDER"));
    }

    @Test
    void arquivar_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        Usuario alvo = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/usuarios/{id}", alvo.getId()), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_admin_permitidoCom204() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Usuario alvo = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/usuarios/{id}", alvo.getId()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void concederCapacidade_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        Usuario alvo = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/usuarios/{id}/capacidades", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"capacidade\":\"SECRETARIO\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void concederCapacidade_admin_permitidoCom201() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Usuario alvo = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        post("/usuarios/{id}/capacidades", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"capacidade\":\"SECRETARIO\"}"),
                        admin))
                .andExpect(status().isCreated());
    }
}
