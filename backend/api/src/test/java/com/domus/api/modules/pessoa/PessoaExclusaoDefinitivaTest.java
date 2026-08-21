package com.domus.api.modules.pessoa;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.PapelCelula;
import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinte;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinteRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.Ministerio;
import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.MinisterioMembroRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.ministerio.Papel;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Direito de eliminação (LGPD): excluir uma pessoa de vez não pode bloquear pelo próprio
 * histórico dela (diferente de Categoria) — contribuição/inscrição sobrevivem anonimizadas
 * (contam no relatório), célula/ministério desvinculam, usuário é apagado junto.
 */
@SpringBootTest
@Transactional
class PessoaExclusaoDefinitivaTest implements PostgresTestContainerSupport {

    @Autowired PessoaService pessoaService;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired MinisterioRepository ministerioRepository;
    @Autowired MinisterioMembroRepository ministerioMembroRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired MovimentacaoContribuinteRepository contribuinteRepository;
    @Autowired VisitanteRepository visitanteRepository;
    @Autowired com.domus.api.modules.outbox.OutboxRepository outboxRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Exclusão Pessoa " + UUID.randomUUID())
                .emailContato("excl-" + UUID.randomUUID() + "@teste.com")
                .build());
    }

    private Pessoa pessoa(String nome) {
        return pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome(nome).vinculo(Vinculo.MEMBRO).build());
    }

    @Test
    void excluirDefinitivo_desvinculaTudoEApagaUsuario_semBloquearENuncaApagandoOutraPessoa() {
        Pessoa alvo = pessoa("Alvo da Exclusão " + UUID.randomUUID());
        Pessoa outra = pessoa("Não Deve Ser Tocada " + UUID.randomUUID());
        String nomeAlvo = alvo.getNome();
        entityManager.flush();

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(alvo).role(role).ativo(true).build());
        UUID usuarioId = usuario.getId();
        entityManager.flush();

        Celula celula = celulaRepository.save(Celula.builder().igreja(igreja).nome("Célula " + UUID.randomUUID()).build());
        CelulaMembro membroCelula = celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(alvo).papel(PapelCelula.MEMBRO).build());
        UUID membroCelulaId = membroCelula.getId();
        // Vínculo de OUTRA pessoa na mesma tabela — prova que os deletes/updates com
        // pessoa_id "cru" não vazam pra outros registros.
        CelulaMembro membroCelulaDaOutra = celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(outra).papel(PapelCelula.MEMBRO).build());
        UUID membroCelulaDaOutraId = membroCelulaDaOutra.getId();
        entityManager.flush();

        Ministerio ministerio = ministerioRepository.save(Ministerio.builder().igreja(igreja).nome("Rede " + UUID.randomUUID()).build());
        ministerioMembroRepository.save(MinisterioMembro.builder()
                .igreja(igreja).ministerio(ministerio).pessoa(alvo).papel(Papel.MEMBRO).build());
        entityManager.flush();

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1))
                .responsavel(alvo).build());
        UUID eventoId = evento.getId();
        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(alvo).status(StatusInscricao.CONFIRMADA).build());
        UUID inscricaoId = inscricao.getId();
        entityManager.flush();

        CategoriaFinanceira categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Dízimo " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA).build());
        MovimentacaoFinanceira movimentacao = movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());
        entityManager.flush();
        MovimentacaoContribuinte contribuicao = contribuinteRepository.save(MovimentacaoContribuinte.builder()
                .movimentacao(movimentacao).pessoa(alvo).valor(BigDecimal.TEN).build());
        UUID contribuicaoId = contribuicao.getId();
        entityManager.flush();

        Visitante visitante = visitanteRepository.save(Visitante.builder()
                .igreja(igreja).nome("Visitante Convertido " + UUID.randomUUID())
                .convertidoPessoaId(alvo.getId()).build());
        UUID visitanteId = visitante.getId();
        entityManager.flush();

        // Arquiva via SQL direto (não pessoaRepository.delete): com tantas FKs apontando pra
        // alvo, o evento de REMOVE do Hibernate cascateia checagens na sessão inteira e
        // esbarra num falso positivo de "instância transiente" — o @SQLDelete de verdade
        // (chamado pelo fluxo real de arquivar) nunca passa por esse caminho.
        entityManager.createNativeQuery("UPDATE pessoa SET deleted_at = NOW() WHERE id = :id")
                .setParameter("id", alvo.getId()).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        pessoaService.excluirDefinitivo(alvo.getId(), igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(pessoaRepository.findByIdAndIgrejaIdIncluindoArquivadas(alvo.getId(), igreja.getId())).isEmpty();
        assertThat(usuarioRepository.findByPessoaIdIncluindoArquivados(alvo.getId())).isEmpty();
        assertThat(usuarioRepository.findById(usuarioId)).isEmpty();

        // Célula/ministério: desvincula de vez (some das duas listas).
        assertThat(celulaMembroRepository.findById(membroCelulaId)).isEmpty();
        assertThat(ministerioMembroRepository.findByMinisterioIdAndPessoaId(ministerio.getId(), alvo.getId())).isEmpty();
        // Vínculo da OUTRA pessoa na mesma tabela não é tocado.
        assertThat(celulaMembroRepository.findById(membroCelulaDaOutraId)).isPresent();

        // Responsável de evento: desvincula com fallback de texto, igual ao arquivar.
        Evento eventoDepois = eventoRepository.findById(eventoId).orElseThrow();
        assertThat(eventoDepois.getResponsavel()).isNull();
        assertThat(eventoDepois.getResponsavelTexto()).isEqualTo(nomeAlvo);

        // Inscrição e contribuição: a linha sobrevive, pessoa anonimizada.
        InscricaoEvento inscricaoDepois = inscricaoRepository.findById(inscricaoId).orElseThrow();
        assertThat(inscricaoDepois.getPessoa()).isNull();

        MovimentacaoContribuinte contribuicaoDepois = contribuinteRepository.findById(contribuicaoId).orElseThrow();
        assertThat(contribuicaoDepois.getPessoa()).isNull();
        assertThat(contribuicaoDepois.getValor()).isEqualByComparingTo(BigDecimal.TEN);

        // Reindexa a movimentação na busca — senão o nome antigo fica preso no Elasticsearch.
        boolean movimentacaoReindexada = outboxRepository.findAll().stream()
                .anyMatch(o -> o.getEntidadeId().equals(movimentacao.getId())
                        && o.getTipoEntidade() == com.domus.api.modules.outbox.TipoEntidadeOutbox.MOVIMENTACAO);
        assertThat(movimentacaoReindexada).isTrue();

        // Visitante: mantém o registro de conversão, só marca a pessoa como removida.
        Visitante visitanteDepois = visitanteRepository.findById(visitanteId).orElseThrow();
        assertThat(visitanteDepois.getConvertidoPessoaId()).isNull();
        assertThat(visitanteDepois.isConvertidoPessoaRemovida()).isTrue();

        // Nunca apaga outra pessoa.
        assertThat(pessoaRepository.findByIdAndIgrejaId(outra.getId(), igreja.getId())).isPresent();
    }

    @Test
    void excluirDefinitivo_falhaQuandoPessoaNaoEncontrada() {
        assertThatThrownBy(() -> pessoaService.excluirDefinitivo(UUID.randomUUID(), igreja.getId()))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }
}
