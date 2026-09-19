package com.domus.api.modules.financeiro.balancete;

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

import java.time.Year;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao balancete
 * financeiro. Mesmo padrão de `Relatorio`: capacidade "TESOUREIRO", mais uma regra própria
 * — o balancete do grupo (`/balancete-anual/congregacoes`) só é visível pela igreja SEDE;
 * uma congregação (filha) tentando ver é barrada, mesmo sendo ADMIN dela.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BalanceteControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Balancete " + UUID.randomUUID())
                .emailContato("balancete-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();
    }

    private Usuario usuarioComRoleNaIgreja(String nomeRole, Igreja igrejaAlvo) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igrejaAlvo).nome("Login Teste " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome(nomeRole).orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igrejaAlvo).pessoa(pessoa).role(role).ativo(true).build());
        entityManager.flush();
        return usuario;
    }

    private Usuario usuarioComRole(String nomeRole) {
        return usuarioComRoleNaIgreja(nomeRole, igreja);
    }

    private void concederCapacidade(Usuario usuario, String capacidade) {
        usuarioCapacidadeRepository.save(UsuarioCapacidade.builder()
                .usuarioId(usuario.getId()).capacidade(capacidade).build());
        entityManager.flush();
    }

    @Test
    void balanceteAnual_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/relatorios/balancete-anual").param("ano", String.valueOf(Year.now().getValue())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void balanceteAnual_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        get("/relatorios/balancete-anual").param("ano", String.valueOf(Year.now().getValue())),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void balanceteAnual_acessoComumComCapacidadeTesoureiro_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        concederCapacidade(comum, "TESOUREIRO");

        mockMvc.perform(auth.autenticado(
                        get("/relatorios/balancete-anual").param("ano", String.valueOf(Year.now().getValue())),
                        comum))
                .andExpect(status().isOk());
    }

    @Test
    void balanceteAnual_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        get("/relatorios/balancete-anual").param("ano", String.valueOf(Year.now().getValue())),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void balanceteFamilia_sede_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        get("/relatorios/balancete-anual/congregacoes").param("ano", String.valueOf(Year.now().getValue())),
                        admin))
                .andExpect(status().isOk());
    }

    @Test
    void balanceteFamilia_congregacao_recusaCom403() throws Exception {
        Igreja congregacao = igrejaRepository.save(Igreja.builder()
                .nome("Congregação " + UUID.randomUUID())
                .emailContato("congregacao-" + UUID.randomUUID() + "@teste.com")
                .igrejaMae(igreja)
                .build());
        entityManager.flush();
        Usuario adminDaCongregacao = usuarioComRoleNaIgreja("ADMIN_IGREJA", congregacao);

        mockMvc.perform(auth.autenticado(
                        get("/relatorios/balancete-anual/congregacoes").param("ano", String.valueOf(Year.now().getValue())),
                        adminDaCongregacao))
                .andExpect(status().isForbidden());
    }
}
