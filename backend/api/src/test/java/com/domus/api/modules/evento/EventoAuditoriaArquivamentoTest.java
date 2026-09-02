package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.PessoaService;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.usuario.UsuarioService;
import com.domus.api.shared.DTO.PagedResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Arquivar a PESSOA responsável ou o USUÁRIO que cadastrou/atualizou um evento nunca pode derrubar {@code GET /eventos}:
 * como {@code Pessoa}/{@code Usuario} usam soft delete, o {@code ON DELETE SET NULL} das FKs nunca dispara de verdade —
 * sem o desvínculo explícito, o proxy LAZY apontaria pra linha escondida e estouraria {@code EntityNotFoundException}
 * dentro do {@code .map()} da página, quebrando a listagem inteira.
 */
@SpringBootTest
@Transactional
class EventoAuditoriaArquivamentoTest implements PostgresTestContainerSupport {

    @Autowired PessoaService pessoaService;
    @Autowired UsuarioService usuarioService;
    @Autowired EventoService eventoService;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired com.domus.api.modules.evento.EventoResponsavelRepository eventoResponsavelRepository;
    @Autowired EntityManager entityManager;

    UUID igrejaId;
    Igreja igrejaDoTeste;

    @BeforeEach
    void setup() {
        igrejaDoTeste = igrejaRepository.save(novaIgreja("Igreja do Teste de Auditoria"));
        igrejaId = igrejaDoTeste.getId();
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        return igreja;
    }

    private Pessoa novaPessoa(String nome) {
        Pessoa pessoa = Pessoa.builder()
                .igreja(igrejaDoTeste)
                .nome(nome)
                .vinculo(Vinculo.MEMBRO)
                .build();
        return pessoaRepository.save(pessoa);
    }

    private Usuario novoUsuario(Pessoa pessoa, String roleNome) {
        Role role = roleRepository.findByNome(roleNome).orElseThrow();
        Usuario usuario = Usuario.builder()
                .igreja(igrejaDoTeste)
                .pessoa(pessoa)
                .ativo(true)
                .role(role)
                .build();
        return usuarioRepository.save(usuario);
    }

    private EventoResponse listarEIsolarEvento(UUID eventoId) {
        PagedResponse<EventoResponse> pagina =
                eventoService.listarEventos(igrejaId, null, null, null, "ADMIN_IGREJA", PageRequest.of(0, 20));
        return pagina.getContent().stream()
                .filter(r -> r.id().equals(eventoId)).findFirst().orElseThrow();
    }

    @Test
    void arquivar_a_pessoa_responsavel_nao_apaga_o_evento() {
        Pessoa responsavel = novaPessoa("Ana Responsável");
        Usuario criador = novoUsuario(novaPessoa("Carlos Criador"), "ADMIN_IGREJA");

        Evento evento = Evento.builder()
                .igreja(igrejaDoTeste)
                .titulo("Café dos Homens")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .criadoPor(criador)
                .exclusivoMembros(false)
                .requerInscricao(false)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();
        eventoResponsavelRepository.save(com.domus.api.modules.evento.EventoResponsavel.builder()
                .igreja(igrejaDoTeste).evento(evento).pessoa(responsavel).build());
        entityManager.flush();

        pessoaService.arquivarMembro(responsavel.getId(), igrejaId);

        // Força releitura do banco — sem isto, a instância já gerenciada na sessão manteria o
        // proxy antigo em memória e o teste não provaria nada (ver LocalEventoServiceTest).
        entityManager.flush();
        entityManager.clear();

        // Caminho REAL que quebrava: EventoService.listarEventos monta EventoResponse, que
        // resolveria o proxy LAZY de responsavel.
        EventoResponse resposta = listarEIsolarEvento(eventoId);

        assertThat(resposta.responsaveis()).hasSize(1);
        assertThat(resposta.responsaveis().get(0).nome()).isEqualTo("Ana Responsável");
        assertThat(resposta.responsaveis().get(0).id()).isNull(); // não é mais um cadastro navegável

        Evento recarregado = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId).orElseThrow();
        assertThat(recarregado.getResponsaveis()).hasSize(1);
        assertThat(recarregado.getResponsaveis().get(0).getPessoa()).isNull();
        assertThat(recarregado.getResponsaveis().get(0).getNomeTexto()).isEqualTo("Ana Responsável");

        // A pessoa em si foi arquivada — não aparece mais para a igreja.
        assertThat(pessoaRepository.findByIdAndIgrejaId(responsavel.getId(), igrejaId)).isEmpty();
    }

    @Test
    void arquivar_o_usuario_que_criou_o_evento_nao_apaga_a_listagem() {
        Pessoa pessoaDoCriador = novaPessoa("Bruno Criador");
        Usuario criador = novoUsuario(pessoaDoCriador, "ADMIN_IGREJA");

        Evento evento = Evento.builder()
                .igreja(igrejaDoTeste)
                .titulo("Culto de Celebração")
                .inicioEm(LocalDateTime.now().plusDays(3))
                .criadoPor(criador)
                .exclusivoMembros(false)
                .requerInscricao(false)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();

        // Arquiva a PESSOA (cascateia para arquivarPorMembro); outro admin evita ULTIMO_ADMIN.
        novoUsuario(novaPessoa("Outro Admin"), "ADMIN_IGREJA");

        pessoaService.arquivarMembro(pessoaDoCriador.getId(), igrejaId);

        entityManager.flush();
        entityManager.clear();

        // Caminho REAL que quebrava: EventoResponse.PessoaResumo.deUsuario chama
        // u.getPessoa().getNome() e resolveria o proxy LAZY de criadoPor.
        EventoResponse resposta = listarEIsolarEvento(eventoId);

        assertThat(resposta.criadoPor().nome()).isEqualTo("Bruno Criador");
        assertThat(resposta.criadoPor().id()).isNull(); // não é mais um cadastro navegável

        Evento recarregado = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId).orElseThrow();
        assertThat(recarregado.getCriadoPor()).isNull();
        assertThat(recarregado.getCriadoPorTexto()).isEqualTo("Bruno Criador");

        assertThat(usuarioRepository.findByIdAndIgrejaId(criador.getId(), igrejaId)).isEmpty();
    }
}
