package com.domus.api.modules.igreja.familia.consolidado;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Complemento HTTP do já existente `ConsolidadoControllerTest` (Mockito puro, chama o
 * controller direto — não prova a `SecurityFilterChain`). O próprio controller documenta
 * o risco: "Matcher de /relatorios/** libera ADMIN/LÍDER/COMUM, então a autorização
 * financeira real é feita aqui dentro" — só um teste passando pela chain de verdade prova
 * que essa afirmação continua batendo (ver `AutenticacaoTestSupport`).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsolidadoControllerAutorizacaoTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Consolidado " + UUID.randomUUID())
                .emailContato("consolidado-" + UUID.randomUUID() + "@teste.com")
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

    private String rota() {
        return "/relatorios/congregacoes?dataInicio=" + LocalDate.now().minusDays(30) + "&dataFim=" + LocalDate.now();
    }

    @Test
    void consolidado_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get(rota()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consolidado_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRoleNaIgreja("ACESSO_COMUM", igreja);

        mockMvc.perform(auth.autenticado(get(rota()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void consolidado_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRoleNaIgreja("ADMIN_IGREJA", igreja);

        mockMvc.perform(auth.autenticado(get(rota()), admin))
                .andExpect(status().isOk());
    }

    @Test
    void consolidado_congregacao_recusaCom403() throws Exception {
        Igreja congregacao = igrejaRepository.save(Igreja.builder()
                .nome("Congregação " + UUID.randomUUID())
                .emailContato("congregacao-" + UUID.randomUUID() + "@teste.com")
                .igrejaMae(igreja)
                .build());
        entityManager.flush();
        Usuario adminDaCongregacao = usuarioComRoleNaIgreja("ADMIN_IGREJA", congregacao);

        mockMvc.perform(auth.autenticado(get(rota()), adminDaCongregacao))
                .andExpect(status().isForbidden());
    }
}
