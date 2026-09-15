package com.domus.api.modules.ministerio;

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
 * ministérios — mesma estrutura de `Celula`: ADMIN/SECRETARIO gerencia o cadastro; editar
 * ou mexer em membros exige ADMIN/SECRETARIO OU o LÍDER DESTE ministério específico
 * (`MinisterioMembro.papel=LIDER` + `status=ATIVO`), não o role LIDER genérico nem líder
 * de OUTRO ministério.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MinisterioControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    @Autowired MinisterioRepository ministerioRepository;
    @Autowired MinisterioMembroRepository ministerioMembroRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Ministerio " + UUID.randomUUID())
                .emailContato("ministerio-" + UUID.randomUUID() + "@teste.com")
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

    private Ministerio ministerio() {
        Ministerio m = ministerioRepository.save(Ministerio.builder()
                .igreja(igreja).nome("Ministério Teste " + UUID.randomUUID()).build());
        entityManager.flush();
        return m;
    }

    private void tornarLiderDoMinisterio(Ministerio ministerio, Usuario usuario) {
        ministerioMembroRepository.save(MinisterioMembro.builder()
                .igreja(igreja).ministerio(ministerio).pessoa(usuario.getPessoa())
                .papel(Papel.LIDER).status(StatusMembro.ATIVO)
                .build());
        entityManager.flush();
    }

    private String corpoMinimo(String nome) {
        return "{\"nome\":\"" + nome + "\"}";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/ministerios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/ministerios"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void criar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/ministerios").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Novo Ministério")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_liderComCapacidadeSecretario_permitidoCom201() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        concederCapacidade(lider, "SECRETARIO");

        mockMvc.perform(auth.autenticado(
                        post("/ministerios").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Novo Ministério")),
                        lider))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Novo Ministério"));
    }

    @Test
    void criar_semNome_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/ministerios").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void criar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/ministerios")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo("Novo Ministério")))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_liderQueNaoEhLiderDesteMinisterio_recusaCom403() throws Exception {
        Ministerio m = ministerio();
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(
                        put("/ministerios/{id}", m.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editado")),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_liderDoProprioMinisterio_permitidoCom200() throws Exception {
        Ministerio m = ministerio();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDoMinisterio(m, lider);

        mockMvc.perform(auth.autenticado(
                        put("/ministerios/{id}", m.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editado")),
                        lider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Editado"));
    }

    @Test
    void atualizar_admin_permitidoCom200() throws Exception {
        Ministerio m = ministerio();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/ministerios/{id}", m.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editado")),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void arquivar_acessoComum_recusaCom403() throws Exception {
        Ministerio m = ministerio();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/ministerios/{id}", m.getId()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_admin_permitidoCom204() throws Exception {
        Ministerio m = ministerio();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(delete("/ministerios/{id}", m.getId()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void adicionarMembro_liderDeOutroMinisterio_recusaCom403() throws Exception {
        Ministerio alvo = ministerio();
        Ministerio outroMinisterio = ministerioRepository.save(Ministerio.builder()
                .igreja(igreja).nome("Outro Ministério " + UUID.randomUUID()).build());
        entityManager.flush();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDoMinisterio(outroMinisterio, lider);
        Pessoa novoMembro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Novo Membro " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        entityManager.flush();

        mockMvc.perform(auth.autenticado(
                        post("/ministerios/{id}/membros", alvo.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + novoMembro.getId() + "\"}"),
                        lider))
                .andExpect(status().isForbidden());
    }

    @Test
    void adicionarMembro_liderDoProprioMinisterio_permitidoCom201() throws Exception {
        Ministerio m = ministerio();
        Usuario lider = usuarioComRole("LIDER");
        tornarLiderDoMinisterio(m, lider);
        Pessoa novoMembro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Novo Membro " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        entityManager.flush();

        mockMvc.perform(auth.autenticado(
                        post("/ministerios/{id}/membros", m.getId()).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"pessoaId\":\"" + novoMembro.getId() + "\"}"),
                        lider))
                .andExpect(status().isCreated());
    }

    /** Qualquer pessoa logada pede entrada — sem exigirAdmin, ver controller. */
    @Test
    void pedirEntrada_acessoComum_permitidoCom201() throws Exception {
        Ministerio m = ministerio();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(post("/ministerios/{id}/pedidos", m.getId()), comum))
                .andExpect(status().isCreated());
    }
}
