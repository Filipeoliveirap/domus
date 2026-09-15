package com.domus.api.modules.evento.campopersonalizado;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Harness de autorização por endpoint (ver AutenticacaoTestSupport) aplicado ao módulo de
 * campos personalizados de evento. Sem checagem no controller — toda a restrição mora no
 * `SecurityConfig` genérico de `/eventos/**` (GET liberado a todo perfil, PUT restrito a
 * ADMIN/LIDER), já que este endpoint é aninhado sob `/eventos/{id}/campos-personalizados`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CampoPersonalizadoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Evento evento;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Campo " + UUID.randomUUID())
                .emailContato("campo-" + UUID.randomUUID() + "@teste.com")
                .build());
        evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento Teste " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(10))
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

    private String corpoMinimo() {
        return "[{\"label\":\"Camisa\",\"tipo\":\"TEXTO_CURTO\",\"obrigatorio\":false,"
                + "\"visivelAoPublico\":true,\"ordem\":0}]";
    }

    @Test
    void listar_semAutenticacao_recusaCom401() throws Exception {
        mockMvc.perform(get("/eventos/{eventoId}/campos-personalizados", evento.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(get("/eventos/{eventoId}/campos-personalizados", evento.getId()), comum))
                .andExpect(status().isOk());
    }

    @Test
    void salvar_acessoComum_recusaCom403() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{eventoId}/campos-personalizados", evento.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        comum))
                .andExpect(status().isForbidden());
    }

    @Test
    void salvar_admin_permitidoCom200() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{eventoId}/campos-personalizados", evento.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(corpoMinimo()),
                        admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Camisa"));
    }

    @Test
    void salvar_semLabel_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{eventoId}/campos-personalizados", evento.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"tipo\":\"TEXTO_CURTO\",\"obrigatorio\":false,"
                                        + "\"visivelAoPublico\":true,\"ordem\":0}]"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salvar_opcaoUnicaSemOpcoes_recusaCom400() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(auth.autenticado(
                        put("/eventos/{eventoId}/campos-personalizados", evento.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[{\"label\":\"Tamanho\",\"tipo\":\"OPCAO_UNICA\",\"obrigatorio\":true,"
                                        + "\"visivelAoPublico\":true,\"ordem\":0}]"),
                        admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salvar_semTokenCsrf_recusaCom403() throws Exception {
        Usuario admin = usuarioComRole("ADMIN_IGREJA");

        mockMvc.perform(put("/eventos/{eventoId}/campos-personalizados", evento.getId())
                        .cookie(auth.cookieDe(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoMinimo()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarParaMinhaResposta_acessoComum_permitidoCom200() throws Exception {
        Usuario comum = usuarioComRole("ACESSO_COMUM");

        mockMvc.perform(auth.autenticado(
                        get("/eventos/{eventoId}/campos-personalizados/minha", evento.getId()), comum))
                .andExpect(status().isOk());
    }
}
