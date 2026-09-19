package com.domus.api.modules.igreja.familia;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado a igrejas
 * vinculadas (sede/congregações). `SecurityConfig`: `GET /igrejas-vinculadas` liberado a
 * todo perfil, mas `/igrejas-vinculadas/**` (gerar código, entrar, desvincular, sair) é só
 * ADMIN. Caso próprio do módulo: `codigoVinculo`/`codigoGeradoEm` só aparecem na resposta
 * pra ADMIN — LIDER/COMUM veem o resto do status mascarado (código de vínculo é
 * credencial: quem tem o código entra na família).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VinculoControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Vinculo " + UUID.randomUUID())
                .emailContato("vinculo-" + UUID.randomUUID() + "@teste.com")
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
    void status_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/igrejas-vinculadas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/igrejas-vinculadas"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void status_acessoComum_naoVeCodigoVinculo() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        mockMvc.perform(auth.autenticado(post("/igrejas-vinculadas/codigo"), admin))
                .andExpect(status().isOk());

        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/igrejas-vinculadas"), comum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoVinculo").doesNotExist());
    }

    @Test
    void status_admin_veCodigoVinculoAposGerar() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        mockMvc.perform(auth.autenticado(post("/igrejas-vinculadas/codigo"), admin))
                .andExpect(status().isOk());

        mockMvc.perform(auth.autenticado(get("/igrejas-vinculadas"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoVinculo").isNotEmpty());
    }

    @Test
    void gerarCodigo_liderSemPapelAdmin_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(post("/igrejas-vinculadas/codigo"), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerarCodigo_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(post("/igrejas-vinculadas/codigo"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoVinculo").isNotEmpty());
    }

    @Test
    void gerarCodigo_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/igrejas-vinculadas/codigo").cookie(auth.cookieDe(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void entrar_liderSemPapelAdmin_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        post("/igrejas-vinculadas/entrar").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"codigo\":\"ABCD1234\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void entrar_admin_semCodigo_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/igrejas-vinculadas/entrar").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void desvincular_liderSemPapelAdmin_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        delete("/igrejas-vinculadas/congregacoes/{id}", UUID.randomUUID()), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void sair_liderSemPapelAdmin_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(delete("/igrejas-vinculadas/sair"), lider))
                .andExpect(status().isForbidden());
    }
}
