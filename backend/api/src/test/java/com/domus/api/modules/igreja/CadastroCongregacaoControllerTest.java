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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CadastroCongregacaoControllerTest implements PostgresTestContainerSupport {

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.igrejaNome").value("Igreja Filha Centenário"))
                .andExpect(jsonPath("$.nome").value("Líder Marcos"));

        var optConvite = codigoConviteRepository.findByCodigo(codigoConvite);
        assertThat(optConvite).isPresent();
        assertThat(optConvite.get().getUsadoEm()).isNotNull();
        assertThat(optConvite.get().getIgrejaFilha().getIgrejaMae().getId()).isEqualTo(matriz.getId());
    }
}
