package com.domus.api.modules.usuario;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.DTO.UsuarioArquivadoResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Diferente da pessoa (que carrega o login junto), aqui é o próprio módulo Usuários: excluir
 * de vez apaga só o login, a pessoa e o histórico dela (evento como criador, p.ex.) continuam
 * intactos — nunca bloqueia (LGPD, direito de eliminação do próprio login).
 */
@SpringBootTest
@Transactional
class UsuarioArquivadoExclusaoTest implements PostgresTestContainerSupport {

    @Autowired UsuarioService usuarioService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Usuário Arquivado " + UUID.randomUUID())
                .emailContato("usrarq-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();
    }

    @Test
    void arquivar_listarArquivados_restaurarEExcluirDefinitivo_semApagarAPessoa() {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Login Teste " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        String nomePessoa = pessoa.getNome();
        Role role = roleRepository.findByNome("LIDER").orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
        UUID usuarioId = usuario.getId();

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1))
                .criadoPor(usuario).build());
        UUID eventoId = evento.getId();
        entityManager.flush();

        usuarioService.arquivarUsuario(usuarioId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        List<UsuarioArquivadoResponse> arquivados = usuarioService.listarArquivados(igreja.getId());
        assertThat(arquivados).hasSize(1);
        assertThat(arquivados.get(0).id()).isEqualTo(usuarioId);
        assertThat(arquivados.get(0).nome()).isEqualTo(nomePessoa);

        usuarioService.restaurar(usuarioId, igreja.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(usuarioService.listarArquivados(igreja.getId())).isEmpty();
        assertThat(usuarioRepository.findByIdAndIgrejaId(usuarioId, igreja.getId())).isPresent();

        usuarioService.arquivarUsuario(usuarioId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        usuarioService.excluirDefinitivo(usuarioId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(usuarioRepository.findByIdAndIgrejaIdIncluindoArquivados(usuarioId, igreja.getId())).isEmpty();

        // Login sumiu, mas a pessoa (e o histórico dela) continuam intactos.
        assertThat(pessoaRepository.findByIdAndIgrejaId(pessoa.getId(), igreja.getId())).isPresent();
        Evento eventoDepois = eventoRepository.findById(eventoId).orElseThrow();
        assertThat(eventoDepois.getCriadoPor()).isNull();
        assertThat(eventoDepois.getCriadoPorTexto()).isEqualTo(nomePessoa);
    }

    @Test
    void excluirDefinitivo_falhaQuandoUsuarioNaoEncontrado() {
        assertThatThrownBy(() -> usuarioService.excluirDefinitivo(UUID.randomUUID(), igreja.getId()))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }
}
