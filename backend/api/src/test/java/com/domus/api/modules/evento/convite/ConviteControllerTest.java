package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConviteControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired EntityManager entityManager;

    Igreja igreja;
    Pessoa convidante;
    Evento evento;
    InscricaoEvento inscricaoConvidante;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Convite " + java.util.UUID.randomUUID())
                .emailContato("convite-" + java.util.UUID.randomUUID() + "@teste.com")
                .build());

        convidante = pessoaRepository.save(Pessoa.builder().igreja(igreja).nome("Ana Convidante")
                .email("ana-" + java.util.UUID.randomUUID() + "@teste.com").vinculo(Vinculo.MEMBRO).build());

        evento = eventoRepository.save(Evento.builder().igreja(igreja).titulo("Culto de Jovens")
                .inicioEm(LocalDateTime.now().plusDays(3)).requerInscricao(true).build());

        inscricaoConvidante = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(convidante).status(StatusInscricao.CONFIRMADA).build());

        entityManager.flush();

        redisTemplate.opsForValue().set("convite:token-teste", inscricaoConvidante.getId().toString());
    }

    @Test
    void getConvitePublicoNaoExigeAutenticacao() throws Exception {
        mockMvc.perform(get("/convites/token-teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Culto de Jovens")));
    }

    @Test
    void getConvitePublicoNaoVazaEmailOuTelefoneDoConvidante() throws Exception {
        mockMvc.perform(get("/convites/token-teste"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(convidante.getEmail()))));
    }

    @Test
    void getConviteComTokenInvalidoDevolve404() throws Exception {
        mockMvc.perform(get("/convites/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fotoComIdQueNaoPertenceAoConviteDevolve404() throws Exception {
        mockMvc.perform(get("/convites/token-teste/fotos/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void entrarComoConvidadoCriaInscricaoEOcupaVaga() throws Exception {
        mockMvc.perform(post("/convites/token-teste/entrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria de Fora\",\"telefone\":\"11999998888\"}"))
                .andExpect(status().isCreated());
    }
}
