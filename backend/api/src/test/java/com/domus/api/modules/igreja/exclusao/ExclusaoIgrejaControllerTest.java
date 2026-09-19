package com.domus.api.modules.igreja.exclusao;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado à exclusão de
 * igreja. `Permissoes.podeExcluirIgreja` é só-ADMIN, sem capacidade extra — ação
 * irreversível (soft, com carência de 10 dias, mas ainda assim). `agendar` com nome de
 * confirmação errado é testado sem senha real (a checagem de nome roda ANTES da
 * reautenticação por senha/Google, ver `ExclusaoIgrejaService.agendar`) — evita depender
 * de hash de senha real só pra provar a rota alcançável.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExclusaoIgrejaControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Exclusao " + UUID.randomUUID())
                .emailContato("exclusao-" + UUID.randomUUID() + "@teste.com")
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
    void resumo_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/igrejas/exclusao/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resumo_liderSemCapacidade_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(get("/igrejas/exclusao/resumo"), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumo_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/igrejas/exclusao/resumo"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void agendar_liderSemCapacidade_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        post("/igrejas/exclusao/agendar").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nomeConfirmacao\":\"" + igreja.getNome() + "\",\"senha\":\"x\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void agendar_semNomeConfirmacao_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/igrejas/exclusao/agendar").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"senha\":\"x\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agendar_nomeConfirmacaoNaoConfere_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/igrejas/exclusao/agendar").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nomeConfirmacao\":\"Nome Errado\",\"senha\":\"x\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agendar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/igrejas/exclusao/agendar")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nomeConfirmacao\":\"" + igreja.getNome() + "\",\"senha\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelar_liderSemCapacidade_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(post("/igrejas/exclusao/cancelar"), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelar_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(post("/igrejas/exclusao/cancelar"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void rodarJob_liderSemCapacidade_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(post("/igrejas/exclusao/rodar-job"), lider))
                .andExpect(status().isForbidden());
    }
}
