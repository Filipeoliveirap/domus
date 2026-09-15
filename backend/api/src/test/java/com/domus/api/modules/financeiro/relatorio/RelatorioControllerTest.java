package com.domus.api.modules.financeiro.relatorio;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * relatórios financeiros. Dois eixos de autorização aqui: `Permissoes.podeVerFinanceiro`
 * (ADMIN ou capacidade TESOUREIRO, mesmo padrão de `MovimentacaoFinanceira`) e
 * `FamiliaIgrejaService.resolverEscopo` — passar `igrejaId` de uma igreja que NÃO é da
 * mesma família (sede/congregações) é o IDOR documentado no comentário do controller
 * ("senão é IDOR"), barrado com `AcessoNegadoException` → 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RelatorioControllerTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Relatorio " + UUID.randomUUID())
                .emailContato("relatorio-" + UUID.randomUUID() + "@teste.com")
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

    private String rota() {
        return "/relatorios/resumo?dataInicio=" + LocalDate.now().minusDays(30) + "&dataFim=" + LocalDate.now();
    }

    @Test
    void resumo_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get(rota()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resumo_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get(rota()), comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumo_acessoComumComCapacidadeTesoureiro_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");
        concederCapacidade(comum, "TESOUREIRO");

        mockMvc.perform(auth.autenticado(get(rota()), comum))
                .andExpect(status().isOk());
    }

    @Test
    void resumo_admin_semIgrejaIdVeAPropria_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get(rota()), admin))
                .andExpect(status().isOk());
    }

    @Test
    void resumo_igrejaIdDeFamiliaDiferente_recusaCom403() throws Exception {
        Igreja igrejaSemRelacao = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Sem Relação " + UUID.randomUUID())
                .emailContato("semrelacao-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        get(rota() + "&igrejaId=" + igrejaSemRelacao.getId()), admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumo_igrejaIdDaCongregacaoDaMesmaFamilia_permitidoCom200() throws Exception {
        Igreja congregacao = igrejaRepository.save(Igreja.builder()
                .nome("Congregação " + UUID.randomUUID())
                .emailContato("congregacao-" + UUID.randomUUID() + "@teste.com")
                .igrejaMae(igreja)
                .build());
        entityManager.flush();

        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        get(rota() + "&igrejaId=" + congregacao.getId()), admin))
                .andExpect(status().isOk());
    }

    @Test
    void porCategoria_acessoComumSemCapacidade_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get(
                        "/relatorios/por-categoria?dataInicio=" + LocalDate.now().minusDays(30)
                                + "&dataFim=" + LocalDate.now()),
                        comum))
                .andExpect(status().isForbidden());
    }
}
