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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado à
 * reindexação do Elasticsearch. Achado escrevendo este teste (2026-09-15): o endpoint não
 * tinha checagem nenhuma no controller e caía no matcher genérico
 * `/admin/**` (`ADMIN`/`LIDER`/`COMUM`) — QUALQUER perfil logado, de qualquer igreja,
 * conseguia disparar uma reindexação GLOBAL (todas as igrejas de uma vez, `findAll()` sem
 * filtro de tenant nenhum, ver `ReindexacaoService.reindexarTudo`). Corrigido com
 * `exigirAdmin()` no controller, mesmo padrão de `ExclusaoIgrejaController`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReindexacaoControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Reindexacao " + UUID.randomUUID())
                .emailContato("reindexacao-" + UUID.randomUUID() + "@teste.com")
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
    void reindexar_semAutenticacaoNemCsrf_recusaCom403() throws Exception {
        mockMvc.perform(post("/admin/reindexacao"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindexar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(post("/admin/reindexacao"), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindexar_lider_recusaCom403() throws Exception {
        Usuario lider = usuarioComRole("LIDER");

        mockMvc.perform(auth.autenticado(post("/admin/reindexacao"), lider))
                .andExpect(status().isForbidden());
    }
}
