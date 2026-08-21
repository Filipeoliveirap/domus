package com.domus.api.modules.notificacao;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacaoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Usuario dono;
    Usuario outroUsuario;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Notificação " + UUID.randomUUID())
                .emailContato("notif-ctrl-" + UUID.randomUUID() + "@teste.com")
                .build());
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();

        Pessoa pessoaDono = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Dono").vinculo(Vinculo.MEMBRO).build());
        dono = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaDono).role(role).ativo(true).build());

        Pessoa pessoaOutro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Outro").vinculo(Vinculo.MEMBRO).build());
        outroUsuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaOutro).role(role).ativo(true).build());

        entityManager.flush();
    }

    private Notificacao notificacaoDe(Usuario destinatario) {
        Notificacao n = notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(destinatario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build());
        entityManager.flush();
        return n;
    }

    @Test
    void listarSoTrazNotificacaoDoProprioUsuario() throws Exception {
        notificacaoDe(dono);
        notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(get("/notificacoes"), dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void contagemNaoLidasContaSoDoUsuarioAutenticado() throws Exception {
        notificacaoDe(dono);
        notificacaoDe(dono);
        notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(get("/notificacoes/contagem-nao-lidas"), dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void marcarComoLida_naoDeixaMarcarNotificacaoDeOutroUsuario() throws Exception {
        Notificacao doOutro = notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(patch("/notificacoes/" + doOutro.getId() + "/lida"), dono))
                .andExpect(status().isNoContent());

        entityManager.clear();
        Notificacao recarregada = notificacaoRepository.findById(doOutro.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(recarregada.isLida())
                .as("dono não pode marcar como lida uma notificação que não é dele")
                .isFalse();
    }

    @Test
    void marcarTodasComoLidasSoAfetaAsDoUsuarioAutenticado() throws Exception {
        notificacaoDe(dono);
        Notificacao doOutro = notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(patch("/notificacoes/lidas"), dono))
                .andExpect(status().isNoContent());

        entityManager.clear();
        Notificacao recarregada = notificacaoRepository.findById(doOutro.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(recarregada.isLida()).isFalse();
    }
}
