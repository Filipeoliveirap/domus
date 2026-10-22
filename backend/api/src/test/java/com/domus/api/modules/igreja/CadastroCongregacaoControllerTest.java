package com.domus.api.modules.igreja;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.dto.CadastroCongregacaoRequest;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AutenticacaoTestSupport;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CadastroCongregacaoControllerTest implements PostgresTestContainerSupport {

    @MockBean StringRedisTemplate redisTemplate;

    @MockBean com.domus.api.modules.pessoa.busca.PessoaSearchRepository pessoaSearchRepository;
    @MockBean com.domus.api.modules.evento.busca.EventoSearchRepository eventoSearchRepository;
    @MockBean com.domus.api.modules.usuario.busca.UsuarioSearchRepository usuarioSearchRepository;
    @MockBean com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository movimentacaoSearchRepository;
    @MockBean com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository categoriaSearchRepository;
    @MockBean com.domus.api.modules.celula.busca.CelulaSearchRepository celulaSearchRepository;
    @MockBean com.domus.api.modules.ministerio.busca.MinisterioSearchRepository ministerioSearchRepository;
    @MockBean com.domus.api.modules.visitante.busca.VisitanteSearchRepository visitanteSearchRepository;

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired CodigoConviteService codigoConviteService;
    @Autowired CodigoConviteRepository codigoConviteRepository;
    @Autowired EntityManager entityManager;
    @Autowired ObjectMapper objectMapper;

    AutenticacaoTestSupport auth;
    Igreja matriz;

    @BeforeEach
    void setup() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.increment(anyString())).thenReturn(1L);

        auth = new AutenticacaoTestSupport(tokenService);
        matriz = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Matriz " + UUID.randomUUID())
                .emailContato("matriz-" + UUID.randomUUID() + "@teste.com")
                .plano(PlanoAssinatura.PRO)
                .statusAssinatura(StatusAssinatura.ATIVA)
                .build());
        entityManager.flush();
    }

    @Test
    void registrarCongregacao_comCodigoValido_cadastraESemCheckout() throws Exception {
        var respCodigo = codigoConviteService.gerarCodigo(matriz);
        String codigoConvite = respCodigo.codigo();

        String emailAdmin = "admin-filha-" + UUID.randomUUID() + "@teste.com";
        CadastroCongregacaoRequest request = new CadastroCongregacaoRequest(
                codigoConvite,
                "Igreja Filha Centenário",
                emailAdmin,
                "Líder Marcos",
                "senha123"
        );

        mockMvc.perform(post("/igrejas/registrar-congregacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igrejaNome").value("Igreja Filha Centenário"))
                .andExpect(jsonPath("$.nome").value("Líder Marcos"));

        var optConvite = codigoConviteRepository.findByCodigo(codigoConvite);
        assertThat(optConvite).isPresent();
        assertThat(optConvite.get().getUsadoEm()).isNotNull();
        assertThat(optConvite.get().getIgrejaFilha().getIgrejaMae().getId()).isEqualTo(matriz.getId());
    }

    @Test
    void consultarCodigo_valido_retornaDadosDaMatriz() throws Exception {
        var respCodigo = codigoConviteService.gerarCodigo(matriz);

        mockMvc.perform(get("/igrejas/convites/" + respCodigo.codigo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VALIDO"))
                .andExpect(jsonPath("$.matrizNome").value(matriz.getNome()));
    }
}
