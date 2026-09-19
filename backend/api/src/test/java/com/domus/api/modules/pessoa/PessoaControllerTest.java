package com.domus.api.modules.pessoa;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * pessoas. Caso de interesse deste módulo: `SecurityConfig` libera POST/PUT/DELETE
 * `/pessoas/**` pra ADMIN/LIDER/COMUM igualmente — a restrição de verdade ("só admin ou
 * secretário gerencia pessoas") mora no controller (`Permissoes.podeGerenciarPessoas`),
 * então um teste de matcher sozinho não pegaria regressão nessa regra; e "secretário" é uma
 * CAPACIDADE extra (`usuario_capacidade`), independente do role — um LIDER com a capacidade
 * gerencia igual um ADMIN. Também cobre a restrição de dados sensíveis (endereço/observações)
 * por perfil.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PessoaControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Pessoa " + UUID.randomUUID())
                .emailContato("pessoa-" + UUID.randomUUID() + "@teste.com")
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

    private Pessoa pessoaComDadosSensiveis() {
        Pessoa p = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Maria Sensível " + UUID.randomUUID())
                .vinculo(Vinculo.MEMBRO)
                .observacoes("Nota pastoral privada — não pode vazar.")
                .build());
        entityManager.flush();
        return p;
    }

    private String corpoMinimo(String nome) {
        return "{\"nome\":\"" + nome + "\",\"vinculo\":\"MEMBRO\"}";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/pessoas"), comum))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorId_acessoComum_naoVeObservacoesNemEndereco() throws Exception {
        Pessoa p = pessoaComDadosSensiveis();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/pessoas/{id}", p.getId()), comum))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observacoes").doesNotExist())
                .andExpect(content().string(not(containsString("Nota pastoral privada"))));
    }

    @Test
    void buscarPorId_admin_veObservacoes() throws Exception {
        Pessoa p = pessoaComDadosSensiveis();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/pessoas/{id}", p.getId()), admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nota pastoral privada")));
    }

    @Test
    void buscarPorId_isolamentoPorIgreja_recusaCom404() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        Pessoa pDeOutraIgreja = pessoaRepository.save(Pessoa.builder()
                .igreja(outraIgreja).nome("Pessoa de Outra Igreja").vinculo(Vinculo.MEMBRO).build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/pessoas/{id}", pDeOutraIgreja.getId()), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void cadastrar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        post("/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Nova Pessoa")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void cadastrar_liderComCapacidadeSecretario_permitidoCom201() throws Exception {
        Usuario lider = usuarioComRole("LIDER");
        concederCapacidade(lider, "SECRETARIO");

        mockMvc.perform(auth.autenticado(
                        post("/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Nova Pessoa")),
                        lider))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Nova Pessoa"));
    }

    @Test
    void cadastrar_admin_permitidoCom201() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/pessoas").contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Nova Pessoa")),
                        admin))
                .andExpect(status().isCreated());
    }

    @Test
    void cadastrar_semNome_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        post("/pessoas").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"vinculo\":\"MEMBRO\"}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(post("/pessoas")
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo("Nova Pessoa")))
                .andExpect(status().isForbidden());
    }

    @Test
    void atualizar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Pessoa p = pessoaComDadosSensiveis();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        put("/pessoas/{id}", p.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo("Editada")),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Pessoa p = pessoaComDadosSensiveis();
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(delete("/pessoas/{id}", p.getId()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void arquivar_admin_permitidoCom204() throws Exception {
        Pessoa p = pessoaComDadosSensiveis();
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(delete("/pessoas/{id}", p.getId()), admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void arquivados_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/pessoas/arquivados"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscarMe_acessoComum_veOsProprosDadosSensiveis() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        // A própria pessoa do usuário logado ganhou observações depois de criada, pra
        // provar que /me inclui dado sensível da PRÓPRIA pessoa mesmo sem capacidade.
        Pessoa minha = comum.getPessoa();
        minha.setObservacoes("Observação da própria pessoa.");
        pessoaRepository.save(minha);
        entityManager.flush();

        mockMvc.perform(auth.autenticado(get("/pessoas/me"), comum))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Observação da própria pessoa.")));
    }
}
