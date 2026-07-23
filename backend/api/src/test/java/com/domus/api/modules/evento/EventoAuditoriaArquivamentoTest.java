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

/**
 * Prova os dois achados CRITICAL da revisão pós-Task-5: arquivar a PESSOA responsável, ou o
 * USUÁRIO que cadastrou/atualizou um evento, nunca pode derrubar {@code GET /eventos}.
 *
 * <p>{@code Pessoa} e {@code Usuario} usam soft delete (@SQLDelete + @SQLRestriction), então o
 * {@code ON DELETE SET NULL} das FKs {@code responsavel_pessoa_id}/{@code criado_por_usuario_id}/
 * {@code atualizado_por_usuario_id} NUNCA dispara de verdade. Sem o desvínculo feito em
 * {@code PessoaService.arquivarMembro}/{@code UsuarioService.arquivarPorMembro} (via
 * {@code EventoRepository.desvincularResponsavel}/{@code desvincularUsuario}), a FK ficaria
 * apontando para uma linha escondida pelo {@code @SQLRestriction} — resolver o proxy LAZY ao
 * montar {@code EventoResponse} estouraria {@code EntityNotFoundException}, e como isso
 * acontece dentro do {@code .map()} da página, a listagem INTEIRA de eventos quebraria (não
 * só o evento afetado).
 *
 * <p>Roda contra Postgres de verdade (não mock) e sempre {@code flush()}+{@code clear()} antes
 * de listar — sem isso a instância já gerenciada na sessão continuaria com o proxy antigo em
 * memória e o teste passaria mesmo com a FK suja (mesmo cuidado de {@code LocalEventoServiceTest}).
 */
@SpringBootTest
@Transactional
class EventoAuditoriaArquivamentoTest {

    @Autowired PessoaService pessoaService;
    @Autowired UsuarioService usuarioService;
    @Autowired EventoService eventoService;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EventoRepository eventoRepository;
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
                eventoService.listarEventos(igrejaId, null, null, null, PageRequest.of(0, 20));
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
                .responsavel(responsavel)
                .criadoPor(criador)
                .exclusivoMembros(false)
                .requerInscricao(false)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();

        pessoaService.arquivarMembro(responsavel.getId(), igrejaId);

        // Força releitura do banco — sem isto, a instância já gerenciada na sessão manteria o
        // proxy antigo em memória e o teste não provaria nada (ver LocalEventoServiceTest).
        entityManager.flush();
        entityManager.clear();

        // Caminho REAL que quebrava: EventoService.listarEventos monta EventoResponse, que
        // resolveria o proxy LAZY de responsavel.
        EventoResponse resposta = listarEIsolarEvento(eventoId);

        assertThat(resposta.responsavel().nome()).isEqualTo("Ana Responsável");
        assertThat(resposta.responsavel().id()).isNull(); // não é mais um cadastro navegável

        Evento recarregado = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId).orElseThrow();
        assertThat(recarregado.getResponsavel()).isNull();
        assertThat(recarregado.getResponsavelTexto()).isEqualTo("Ana Responsável");

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

        // Arquivando a PESSOA (não o usuário direto): é o caminho MAIS grave da revisão —
        // arquivarMembro cascateia para usuarioService.arquivarPorMembro, e criado_por é
        // preenchido em TODO evento (não é opcional como o responsável). Precisa de outro
        // admin ativo para não esbarrar em ULTIMO_ADMIN.
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
