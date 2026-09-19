package com.domus.api.shared.busca;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * busca global. Testes usam {@code q} em branco de propósito — o controller devolve lista
 * vazia sem tocar no service (e portanto sem precisar de Elasticsearch, que este projeto
 * não containeriza pra teste, só Postgres via {@code PostgresTestContainerSupport}) — o que
 * basta pra provar autorização (403/200 antes de qualquer busca de verdade) e `@Size`.
 * `/busca/usuarios`, `/busca/movimentacoes` e `/busca/categorias` têm matcher liberando
 * ADMIN/LIDER/COMUM em `SecurityConfig`, mas a restrição real (ADMIN ou TESOUREIRO) mora
 * no controller — mesmo padrão de `Pessoa`/`MovimentacaoFinanceira`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuscaControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Busca " + UUID.randomUUID())
                .emailContato("busca-" + UUID.randomUUID() + "@teste.com")
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

    @Test
    void buscarPessoas_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/busca/pessoas").param("q", ""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscarPessoas_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/pessoas").param("q", ""), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPessoas_termoAcimaDe200Caracteres_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/busca/pessoas").param("q", "a".repeat(201)), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscarEventos_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/eventos").param("q", ""), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscarGlobal_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/global").param("q", ""), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscarUsuarios_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/usuarios").param("q", ""), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarUsuarios_liderComCapacidadeTesoureiro_permitidoCom200() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        concederCapacidade(lider, "TESOUREIRO");

        mockMvc.perform(auth.autenticado(get("/busca/usuarios").param("q", ""), lider))
                .andExpect(status().isOk());
    }

    @Test
    void buscarUsuarios_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/busca/usuarios").param("q", ""), admin))
                .andExpect(status().isOk());
    }

    @Test
    void buscarMovimentacoes_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/movimentacoes").param("q", ""), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarMovimentacoes_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/busca/movimentacoes").param("q", ""), admin))
                .andExpect(status().isOk());
    }

    @Test
    void buscarCategorias_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/busca/categorias").param("q", ""), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarCategorias_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/busca/categorias").param("q", ""), admin))
                .andExpect(status().isOk());
    }
}
