package com.domus.api.modules.celula;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * células. Sem matcher próprio em `SecurityConfig` (cai no `anyRequest().authenticated()`
 * genérico) — toda a autorização real mora no controller/service:
 * `Permissoes.podeGerenciarCelulas` (ADMIN ou capacidade SECRETARIO) pra criar/excluir, e
 * `exigirAdminOuLider` (ADMIN/SECRETARIO OU o LÍDER DESTA célula específica, via
 * `CelulaMembro.papel`) pra editar/gerenciar membros — LIDER de OUTRA célula não conta.
 * Esse segundo caso é o de maior risco (autorização por dado, não por role) e já teve um
 * bug real corrigido aqui antes (ver memória `celula-visitante-review-e-correcoes`).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CelulaControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Celula " + UUID.randomUUID())
                .emailContato("celula-" + UUID.randomUUID() + "@teste.com")
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

    private Celula celula() {
        Celula c = celulaRepository.save(Celula.builder()
                .igreja(igreja).nome("Célula Teste " + UUID.randomUUID()).build());
        entityManager.flush();
        return c;
    }

    private void tornarLiderDaCelula(Celula celula, Usuario usuario) {
        celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(usuario.getPessoa()).papel(PapelCelula.LIDER)
                .build());
        entityManager.flush();
    }

    private String corpoMinimo(String nome) {
        return "{\"nome\":\"" + nome + "\"}";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/celulas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/celulas"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void criar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/celulas").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Nova Célula")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_liderComCapacidadeSecretario_permitidoCom201() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        concederCapacidade(lider, "SECRETARIO");

        mockMvc.perform(auth.autenticado(
                        post("/celulas").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Nova Célula")),
                        lider))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Nova Célula"));
    }

    @Test
    void criar_semNome_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/celulas").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/celulas")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo("Nova Célula")))
                .andExpect(status().isForbidden());
    }

    /** O caso de maior risco do módulo: LIDER que NÃO é líder DESTA célula específica
     *  precisa ser barrado — autorização por dado, não só por role. */
    @Test
    void atualizar_liderQueNaoEhLiderDestaCelula_recusaCom403() throws Exception {
        Celula c = celula();
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        put("/celulas/{id}", c.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editada")),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_liderDaPropriaCelula_permitidoCom200() throws Exception {
        Celula c = celula();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDaCelula(c, lider);

        mockMvc.perform(auth.autenticado(
                        put("/celulas/{id}", c.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editada")),
                        lider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editada"));
    }

    @Test
    void atualizar_admin_permitidoCom200() throws Exception {
        Celula c = celula();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/celulas/{id}", c.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editada")),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void excluir_acessoComum_recusaCom403() throws Exception {
        Celula c = celula();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/celulas/{id}", c.getId()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void excluir_admin_permitidoCom204() throws Exception {
        Celula c = celula();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(delete("/celulas/{id}", c.getId()), admin))
                .andExpect(status().isNoContent());
    }

    /** Líder da célula pode adicionar membro — mesma regra de `atualizar`. */
    @Test
    void adicionarMembro_liderDaCelula_permitidoCom201() throws Exception {
        Celula c = celula();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDaCelula(c, lider);
        Pessoa novoMembro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Novo Membro " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        entityManager.flush();

        mockMvc.perform(auth.autenticado(
                        post("/celulas/{id}/membros", c.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + novoMembro.getId() + "\"}"),
                        lider))
                .andExpect(status().isCreated());
    }

    @Test
    void adicionarMembro_liderDeOutraCelula_recusaCom403() throws Exception {
        Celula alvo = celula();
        Celula outraCelula = celulaRepository.save(Celula.builder()
                .igreja(igreja).nome("Outra Célula " + UUID.randomUUID()).build());
        entityManager.flush();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDaCelula(outraCelula, lider);
        Pessoa novoMembro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Novo Membro " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        entityManager.flush();

        mockMvc.perform(auth.autenticado(
                        post("/celulas/{id}/membros", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + novoMembro.getId() + "\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }
}
