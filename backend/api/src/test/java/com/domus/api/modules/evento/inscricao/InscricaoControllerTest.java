package com.domus.api.modules.evento.inscricao;

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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// semAutenticacaoRecusa espera 403: sem cookie de sessão, o CsrfFilter recusa a escrita antes
// mesmo de chegar na checagem de autenticação (mesmo comportamento de VisitanteControllerTest).

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InscricaoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Usuario usuarioComum;
    Evento evento;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);

        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Convidado " + UUID.randomUUID())
                .emailContato("convidado-" + UUID.randomUUID() + "@teste.com")
                .build());

        Pessoa pessoaComum = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Comum").email("comum-" + UUID.randomUUID() + "@teste.com")
                .vinculo(Vinculo.MEMBRO).build());

        Role roleComum = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();
        usuarioComum = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaComum).role(roleComum).ativo(true).build());

        evento = eventoRepository.save(Evento.builder().igreja(igreja).titulo("Culto")
                .inicioEm(LocalDateTime.now().plusDays(3)).requerInscricao(true).build());

        entityManager.flush();
    }

    @Test
    void membroComumConseguePendurarConvidadoSemCadastro() throws Exception {
        mockMvc.perform(auth.autenticado(
                        post("/eventos/" + evento.getId() + "/inscricoes/convidados"), usuarioComum)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria de Fora\",\"telefone\":\"11999998888\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void semAutenticacaoRecusa() throws Exception {
        mockMvc.perform(post("/eventos/" + evento.getId() + "/inscricoes/convidados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria de Fora\"}"))
                .andExpect(status().isForbidden());
    }
}
