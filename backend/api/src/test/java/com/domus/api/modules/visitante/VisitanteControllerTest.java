package com.domus.api.modules.visitante;

import com.domus.api.config.TokenService;
import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Piloto do harness de teste de controller (JWT real em cookie + CSRF do spring-security-test,
 * ver AutenticacaoTestSupport) — cobre tanto validação de input (@Valid não acionado era o bug
 * real corrigido em MoverParaCelulaRequest) quanto autorização por perfil (Permissoes.podeGerenciarVisitantes).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VisitanteControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired VisitanteRepository visitanteRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Visitante " + UUID.randomUUID())
                .emailContato("visitante-" + UUID.randomUUID() + "@teste.com")
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

    private Visitante visitante() {
        Visitante v = visitanteRepository.save(Visitante.builder()
                .igreja(igreja).nome("Visitante Teste " + UUID.randomUUID())
                .build());
        entityManager.flush();
        return v;
    }

    private Celula celula() {
        Celula c = celulaRepository.save(Celula.builder()
                .igreja(igreja).nome("Célula Teste " + UUID.randomUUID())
                .build());
        entityManager.flush();
        return c;
    }

    @Test
    void moverParaCelula_semCelulaId_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Visitante v = visitante();

        mockMvc.perform(auth.autenticado(
                        put("/visitantes/{id}/celula", v.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moverParaCelula_admin_move() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Visitante v = visitante();
        Celula c = celula();

        mockMvc.perform(auth.autenticado(
                        put("/visitantes/{id}/celula", v.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"celulaId\":\"" + c.getId() + "\"}"),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void listar_acessoComumSemGestaoDeVisitantes_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/visitantes"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/visitantes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_termoDeBuscaAcimaDe200Caracteres_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        get("/visitantes").param("q", "a".repeat(201)),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moverParaCelula_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");
        Visitante v = visitante();
        Celula c = celula();

        mockMvc.perform(put("/visitantes/{id}/celula", v.getId())
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"celulaId\":\"" + c.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void buscaLeveDevolveApenasIdNomeTelefone() throws Exception {
        Visitante v = Visitante.builder()
                .igreja(igreja).nome("Pedro Visitante " + UUID.randomUUID())
                .telefone("11988887777")
                .observacoes("Observação sensível que não deve vazar aqui.")
                .build();
        v = visitanteRepository.save(v);
        entityManager.flush();

        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/visitantes/busca-leve").param("q", "Pedro"), comum))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(v.getNome())))
                .andExpect(content().string(not(containsString("Observação sensível"))));
    }

    @Test
    void buscaLeveRespeitaIsolamentoPorIgreja() throws Exception {
        Igreja outraIgreja = igrejaRepository.save(Igreja.builder()
                .nome("Outra Igreja " + UUID.randomUUID())
                .emailContato("outra-" + UUID.randomUUID() + "@teste.com")
                .build());
        visitanteRepository.save(Visitante.builder()
                .igreja(outraIgreja).nome("Visitante de Outra Igreja").build());
        entityManager.flush();

        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/visitantes/busca-leve").param("q", "Outra Igreja"), comum))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Visitante de Outra Igreja"))));
    }
}
