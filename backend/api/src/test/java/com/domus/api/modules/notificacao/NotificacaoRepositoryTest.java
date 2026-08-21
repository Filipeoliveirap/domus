package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificacaoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;

    Igreja igreja;
    Usuario usuario;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Notificação " + UUID.randomUUID())
                .emailContato("notif-" + UUID.randomUUID() + "@teste.com")
                .build());
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano").vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();
        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
    }

    @Test
    void contaSoAsNaoLidasDoUsuarioCerto() {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste 1").lida(false).build());
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste 2").lida(true).build());

        assertThat(notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuario.getId())).isEqualTo(1);
    }

    @Test
    void findByIdAndUsuarioDestinatarioId_naoAcertaNotificacaoDeOutroUsuario() {
        Notificacao salva = notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build());

        assertThat(notificacaoRepository.findByIdAndUsuarioDestinatarioId(salva.getId(), UUID.randomUUID()))
                .isEmpty();
        assertThat(notificacaoRepository.findByIdAndUsuarioDestinatarioId(salva.getId(), usuario.getId()))
                .isPresent();
    }

    @Test
    void listaPaginadaOrdenaPorMaisRecente() {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Primeira").lida(false).build());
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Segunda").lida(false).build());

        var pagina = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }
}
