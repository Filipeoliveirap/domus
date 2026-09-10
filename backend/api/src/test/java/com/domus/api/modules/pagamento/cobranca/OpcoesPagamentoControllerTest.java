package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.config.TokenService;
import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cobre os dois endpoints da Task 5: {@code GET /cobrancas/{id}/opcoes-pagamento} (público,
 * pro checkout) e {@code POST /eventos/simular-pagamento} (autenticado, pro form de evento).
 * Os valores exatos de gross-up do cartão são provados em {@code CalculadoraTaxaPagamentoTest};
 * aqui só se verifica a estrutura das opções (quantas, meio, parcelas) e o total do Pix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OpcoesPagamentoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired CobrancaEventoRepository cobrancaRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Pessoa pessoa;
    Usuario usuarioAdmin;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Opcoes " + UUID.randomUUID())
                .emailContato("opcoes-" + UUID.randomUUID() + "@teste.com")
                .build());
        pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Admin Opcoes " + UUID.randomUUID())
                .vinculo(Vinculo.MEMBRO).build());
        Role admin = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        usuarioAdmin = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(admin).ativo(true).build());
        entityManager.flush();
    }

    private Evento evento(boolean aceitaCartao, int maxParcelas) {
        Evento e = eventoRepository.save(Evento.builder()
                .igreja(igreja)
                .titulo("Retiro Pago " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(30))
                .requerInscricao(true)
                .preco(new BigDecimal("100.00"))
                .pagamentoAceitaCartao(aceitaCartao)
                .pagamentoMaxParcelas(maxParcelas)
                .build());
        entityManager.flush();
        return e;
    }

    private UUID cobranca(Evento e, BigDecimal valor) {
        UUID inscricaoId = UUID.randomUUID();
        entityManager.createNativeQuery(
                "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) " +
                "VALUES (?1, ?2, ?3, ?4, 'AGUARDANDO_PAGAMENTO')")
            .setParameter(1, inscricaoId)
            .setParameter(2, igreja.getId())
            .setParameter(3, e.getId())
            .setParameter(4, pessoa.getId())
            .executeUpdate();

        var c = new CobrancaEvento(igreja.getId(), e.getId(), inscricaoId, pessoa.getId(),
            valor, Instant.now().plus(1, ChronoUnit.DAYS), usuarioAdmin.getId(), null);
        c = cobrancaRepository.save(c);
        entityManager.flush();
        return c.getId();
    }

    @Test
    void eventoSoPix_devolveApenasOpcaoPix() throws Exception {
        UUID cobrancaId = cobranca(evento(false, 1), new BigDecimal("100.00"));

        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valorEvento").value(100.00))
            .andExpect(jsonPath("$.opcoes.length()").value(1))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"))
            .andExpect(jsonPath("$.opcoes[0].parcelas").value(1))
            .andExpect(jsonPath("$.opcoes[0].valorTotal").value(101.00));
    }

    @Test
    void eventoComCartaoAte3x_devolvePixMais3FaixasDeCartao() throws Exception {
        UUID cobrancaId = cobranca(evento(true, 3), new BigDecimal("100.00"));

        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(4))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"))
            .andExpect(jsonPath("$.opcoes[1].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[1].parcelas").value(1))
            .andExpect(jsonPath("$.opcoes[2].parcelas").value(2))
            .andExpect(jsonPath("$.opcoes[3].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[3].parcelas").value(3));
    }

    @Test
    void valorAbaixoDoMinimoDeCartao_devolveApenasPix() throws Exception {
        // R$ 0,50: o gross-up de cartão 1x dá ~R$ 0,53, abaixo do mínimo de cartão do MP
        // (R$ 1,00). Nenhuma faixa de cartão é oferecível — só Pix.
        UUID cobrancaId = cobranca(evento(true, 6), new BigDecimal("0.50"));

        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(1))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"));
    }

    @Test
    void valorBaixo_filtraFaixasDeCartaoAbaixoDoMinimoPorParcela() throws Exception {
        // R$ 12,00, teto 6x. 1x e 2x sobrevivem (parcela >= R$ 5,00); 3x em diante cai
        // abaixo do mínimo por parcela (~R$ 13,26 / 3 = R$ 4,42) e é filtrada.
        UUID cobrancaId = cobranca(evento(true, 6), new BigDecimal("12.00"));

        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(3))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"))
            .andExpect(jsonPath("$.opcoes[1].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[1].parcelas").value(1))
            .andExpect(jsonPath("$.opcoes[2].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[2].parcelas").value(2));
    }

    @Test
    void semAutenticacao_endpointResponde() throws Exception {
        UUID cobrancaId = cobranca(evento(false, 1), new BigDecimal("100.00"));

        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk());
    }

    @Test
    void simularPagamento_semAutenticacao_recusaCom401() throws Exception {
        // Com CSRF válido mas sem cookie de sessão: isola a exigência de autenticação
        // (sem o csrf() o filtro de CSRF barra antes com 403 e não se prova nada sobre auth).
        mockMvc.perform(post("/eventos/simular-pagamento").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preco\":100.00,\"aceitaCartao\":true,\"maxParcelas\":6}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void simularPagamento_autenticado_devolveOpcoes() throws Exception {
        mockMvc.perform(auth.autenticado(post("/eventos/simular-pagamento"), usuarioAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preco\":100.00,\"aceitaCartao\":true,\"maxParcelas\":6}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valorEvento").value(100.00))
            .andExpect(jsonPath("$.opcoes.length()").value(7))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"))
            .andExpect(jsonPath("$.opcoes[6].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[6].parcelas").value(6));
    }

    @Test
    void simularPagamento_semCartao_devolveApenasPix() throws Exception {
        mockMvc.perform(auth.autenticado(post("/eventos/simular-pagamento"), usuarioAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preco\":100.00,\"aceitaCartao\":false,\"maxParcelas\":6}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(1))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"));
    }

    @Test
    void simularPagamento_precoAusente_recusaCom400() throws Exception {
        mockMvc.perform(auth.autenticado(post("/eventos/simular-pagamento"), usuarioAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceitaCartao\":true,\"maxParcelas\":6}"))
            .andExpect(status().isBadRequest());
    }
}
