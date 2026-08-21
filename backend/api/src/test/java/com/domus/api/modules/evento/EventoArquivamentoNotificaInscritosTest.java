package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.NotificacaoRepository;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Regressão real (2026-08-21, achado testando no navegador): notificarInscritos, chamado
 * ANTES de eventoRepository.delete(evento) em arquivarEvento, carregava InscricaoEvento
 * (entidade gerenciada, referenciando evento) — no autoflush seguinte (disparado por
 * evictarCacheDeEventosDaFamilia), o Hibernate via evento como "unsaved transient instance"
 * porque o delete (soft) já tinha mexido no estado da entidade na sessão. Mockito puro não
 * pega isso (não tem sessão Hibernate de verdade) — só um teste de integração contra Postgres
 * real reproduz o autoflush. Corrigido trocando a entidade gerenciada por uma projeção de
 * UUID (InscricaoRepository.findPessoaIdsByEventoIdAndStatus).
 */
@SpringBootTest
@Transactional
class EventoArquivamentoNotificaInscritosTest implements PostgresTestContainerSupport {

    @Autowired EventoService eventoService;
    @Autowired InscricaoService inscricaoService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired EntityManager entityManager;

    UUID igrejaId;
    Igreja igrejaDoTeste;

    @BeforeEach
    void setup() {
        igrejaDoTeste = igrejaRepository.save(novaIgreja("Igreja Teste Arquivamento Evento"));
        igrejaId = igrejaDoTeste.getId();
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        return igreja;
    }

    private Pessoa novaPessoa(String nome) {
        return pessoaRepository.save(Pessoa.builder()
                .igreja(igrejaDoTeste).nome(nome).vinculo(Vinculo.MEMBRO).build());
    }

    private Usuario novoUsuario(Pessoa pessoa) {
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .igreja(igrejaDoTeste).pessoa(pessoa).ativo(true).role(role).build());
    }

    @Test
    void arquivarEventoComInscritoConfirmadoNaoLancaExcecaoENotifica() {
        Pessoa pessoaInscrita = novaPessoa("Inscrita Teste");
        Usuario usuarioInscrito = novoUsuario(pessoaInscrita);
        Usuario usuarioAdmin = novoUsuario(novaPessoa("Admin Teste"));

        Evento evento = Evento.builder()
                .igreja(igrejaDoTeste)
                .titulo("Culto de Teste")
                .inicioEm(LocalDateTime.now().plusDays(3))
                .exclusivoMembros(false)
                .requerInscricao(true)
                .vagas(10)
                .build();
        evento = eventoRepository.save(evento);
        UUID eventoId = evento.getId();

        inscricaoService.inscrever(eventoId, pessoaInscrita.getId(), null,
                pessoaInscrita.getId(), "ACESSO_COMUM", false, igrejaId);

        // Simula sessões separadas (inscrever() e arquivarEvento() são requisições HTTP
        // diferentes na vida real, cada uma com seu próprio Hibernate Session) — sem isto, a
        // InscricaoEvento continuaria gerenciada na MESMA sessão do teste, mascarando se o bug
        // é do código de produção ou só um artefato de teste com sessão longa demais.
        entityManager.flush();
        entityManager.clear();

        // O bug real: isto lançava TransientObjectException antes da correção.
        assertThatCode(() -> eventoService.arquivarEvento(eventoId, igrejaId, usuarioAdmin.getId(),
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA))
                .doesNotThrowAnyException();

        var notificacoes = notificacaoRepository.findByUsuarioDestinatarioId(
                usuarioInscrito.getId(), PageRequest.of(0, 10));
        assertThat(notificacoes.getContent())
                .extracting(n -> n.getTipo())
                .contains(TipoNotificacao.EVENTO_ALTERADO);
    }
}
