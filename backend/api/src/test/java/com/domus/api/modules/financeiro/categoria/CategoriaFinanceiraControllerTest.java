package com.domus.api.modules.financeiro.categoria;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioCapacidade;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
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
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * categoria financeira. Mesmo padrão de `MovimentacaoFinanceira`/`Pessoa`: `SecurityConfig`
 * libera `/categorias/**` pra ADMIN/LIDER/COMUM igualmente — a restrição real mora no
 * controller (`Permissoes.podeVerFinanceiro`), com a capacidade extra "TESOUREIRO".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaFinanceiraControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Categoria " + UUID.randomUUID())
                .emailContato("categoria-" + UUID.randomUUID() + "@teste.com")
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

    private void concederCapacidade(Usuario usuario, String capacidade) {
        usuarioCapacidadeRepository.save(UsuarioCapacidade.builder()
                .usuarioId(usuario.getId()).capacidade(capacidade).build());
        entityManager.flush();
    }

    private CategoriaFinanceira categoria() {
        CategoriaFinanceira c = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Categoria Teste " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA)
                .build());
        entityManager.flush();
        return c;
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/categorias"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_acessoComumComCapacidadeTesoureiro_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        concederCapacidade(comum, "TESOUREIRO");

        mockMvc.perform(auth.autenticado(get("/categorias"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscar_isolamentoPorIgreja_recusaCom404() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        CategoriaFinanceira categoriaDeOutra = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(outraIgreja).nome("Categoria de Outra Igreja").tipo(TipoCategoria.SAIDA).build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/categorias/{id}", categoriaDeOutra.getId()), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void cadastrar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/categorias").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Ofertas\",\"tipo\":\"ENTRADA\"}"),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastrar_admin_criaCom201() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/categorias").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nome\":\"Ofertas\",\"tipo\":\"ENTRADA\"}"),
                        admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Ofertas"));
    }

    @Test
    void cadastrar_semNome_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/categorias").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"tipo\":\"ENTRADA\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/categorias")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ofertas\",\"tipo\":\"ENTRADA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_liderComCapacidadeTesoureiro_permitidoCom204() throws Exception {
        CategoriaFinanceira c = categoria();
        Usuario lider = usuarioComRole("LIDER");
        concederCapacidade(lider, "TESOUREIRO");

        mockMvc.perform(auth.autenticado(delete("/categorias/{id}", c.getId()), lider))
                .andExpect(status().isNoContent());
    }

    @Test
    void arquivar_liderSemCapacidade_recusaCom403() throws Exception {
        CategoriaFinanceira c = categoria();
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(delete("/categorias/{id}", c.getId()), lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void excluirDefinitivo_categoriaSemUso_admin_permitidoCom204() throws Exception {
        CategoriaFinanceira c = categoria();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(delete("/categorias/{id}/definitivo", c.getId()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void arquivadas_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/categorias/arquivadas"), comum))
                .andExpect(status().isForbidden());
    }
}
