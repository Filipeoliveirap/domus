package com.domus.api.modules.pagamento.conta;

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
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre o gap apontado na revisão de segurança da Task 4: prova, no nível de HTTP, que
 * o `igrejaId` usado pelo fluxo OAuth do Mercado Pago vem da sessão autenticada
 * (`UsuarioAutenticado`), nunca de um parâmetro da requisição — em especial o `/callback`,
 * onde a fonte errada (o `state` da query string, controlável pelo cliente) já foi corrigida
 * no controller. `MercadoPagoOAuthClient` é mockado porque de fato chama a API do Mercado
 * Pago via HTTP; aqui só interessa provar qual `igrejaId` chega até o repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContaPagamentoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ContaPagamentoIgrejaRepository contaPagamentoIgrejaRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean MercadoPagoOAuthClient mercadoPagoOAuthClient;

    AutenticacaoTestSupport auth;
    Igreja igrejaA;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igrejaA = igrejaRepository.save(Igreja.builder()
                .nome("Igreja A Pagamento " + UUID.randomUUID())
                .emailContato("igreja-a-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();
    }

    private Usuario usuarioComRole(Igreja igreja, String nomeRole) {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Login Teste " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome(nomeRole).orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
        entityManager.flush();
        return usuario;
    }

    @Test
    void status_usuarioAutenticado_respondeOk() throws Exception {
        Usuario admin = usuarioComRole(igrejaA, "ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(get("/pagamentos/conta/status"), admin))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"conectada\":false}"));
    }

    @Test
    void status_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/pagamentos/conta/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void callback_usaIgrejaIdDaSessaoAutenticada_nuncaDeParametroDaRequisicao() throws Exception {
        Igreja igrejaB = igrejaRepository.save(Igreja.builder()
                .nome("Igreja B Pagamento " + UUID.randomUUID())
                .emailContato("igreja-b-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();

        Usuario adminDaIgrejaA = usuarioComRole(igrejaA, "ADMIN_IGREJA");

        when(mercadoPagoOAuthClient.trocarCodePorTokens("code-qualquer"))
                .thenReturn(new MercadoPagoOAuthClient.TokensObtidos(
                        "mp-user-123", "access-token-fake", "refresh-token-fake", 21600L));

        String state = obterStateValido(adminDaIgrejaA);

        // Autenticado como usuário da Igreja A. Mesmo que o antigo `state` pudesse carregar
        // o UUID da Igreja B (o bug corrigido no round anterior), o `igrejaId` sempre veio
        // da sessão — nunca de parâmetro. `state` aqui é o nonce anti-CSRF (Critical 3a),
        // não uma fonte de igrejaId.
        // O endpoint sempre redireciona pro front (nunca deixa o navegador "parado" nesta
        // URL, ver javadoc de ContaPagamentoController.callback) — sucesso vira
        // "?mpConectado=1" na URL de destino, não um corpo JSON.
        mockMvc.perform(auth.autenticado(
                        get("/pagamentos/conta/callback").param("code", "code-qualquer").param("state", state),
                        adminDaIgrejaA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/configuracoes/igreja?mpConectado=1")));

        entityManager.flush();
        entityManager.clear();

        var contaIgrejaA = contaPagamentoIgrejaRepository.findByIgrejaId(igrejaA.getId());
        assertThat(contaIgrejaA).isPresent();
        assertThat(contaIgrejaA.get().getIgrejaId()).isEqualTo(igrejaA.getId());

        var contaIgrejaB = contaPagamentoIgrejaRepository.findByIgrejaId(igrejaB.getId());
        assertThat(contaIgrejaB).isEmpty();
    }

    @Test
    void callback_comStateInvalidoOuInexistente_recusaSemChamarMercadoPago() throws Exception {
        // Critical 3a (revisão final de branch): prova que um `state` forjado/inexistente
        // é recusado ANTES de trocar o code por tokens — a mesma defesa que impede o
        // ataque de CSRF de OAuth descrito no javadoc de MercadoPagoOAuthService.
        Usuario admin = usuarioComRole(igrejaA, "ADMIN_IGREJA");

        // Mesmo motivo do teste de sucesso acima: recusa também vira redirect, com o
        // código do erro na query string ("?mpErro=..."), não um corpo JSON com 400.
        mockMvc.perform(auth.autenticado(
                        get("/pagamentos/conta/callback")
                                .param("code", "code-qualquer")
                                .param("state", "state-forjado-por-atacante"),
                        admin))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/configuracoes/igreja?mpErro=OAUTH_STATE_INVALIDO")));

        var conta = contaPagamentoIgrejaRepository.findByIgrejaId(igrejaA.getId());
        assertThat(conta).isEmpty();
    }

    @Test
    void conectar_desconectar_recusamAcessoComumComForbidden() throws Exception {
        // Critical 3b (revisão final de branch): conectar/desconectar a conta de recebimento
        // da igreja precisa exigir ADMIN_IGREJA — antes desta correção, caía em
        // anyRequest().authenticated() e qualquer perfil (inclusive ACESSO_COMUM) podia
        // mexer na conta de pagamento da igreja inteira.
        Usuario acessoComum = usuarioComRole(igrejaA, "ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/pagamentos/conta/conectar"), acessoComum))
                .andExpect(status().isForbidden());
        mockMvc.perform(auth.autenticado(delete("/pagamentos/conta"), acessoComum))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_qualquerPerfilAutenticadoRespondeOk() throws Exception {
        // Achado ao vivo (2026-08-27): GET /status só devolve um booleano ("conectada") —
        // sem dado sensível. Restringi-lo a ADMIN fazia o front tratar 403 como "não
        // conectada" e bloquear/avisar errado quem não é admin tentando se inscrever ou
        // pagar num evento pago com a conta JÁ conectada.
        Usuario lider = usuarioComRole(igrejaA, "LIDER");
        Usuario acessoComum = usuarioComRole(igrejaA, "ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/pagamentos/conta/status"), lider))
                .andExpect(status().isOk());
        mockMvc.perform(auth.autenticado(get("/pagamentos/conta/status"), acessoComum))
                .andExpect(status().isOk());
    }

    /** Chama o endpoint real de conectar (autenticado como {@code usuario}) e extrai o `state` da URL devolvida. */
    private String obterStateValido(Usuario usuario) throws Exception {
        var resultado = mockMvc.perform(auth.autenticado(get("/pagamentos/conta/conectar"), usuario))
                .andExpect(status().isOk())
                .andReturn();
        String json = resultado.getResponse().getContentAsString();
        String marcador = "state=";
        int inicio = json.indexOf(marcador) + marcador.length();
        // A URL vem dentro de um JSON (`{"url":"...state=XYZ"}`) — corta na aspa de fechamento.
        int fim = json.indexOf('"', inicio);
        return json.substring(inicio, fim);
    }
}
