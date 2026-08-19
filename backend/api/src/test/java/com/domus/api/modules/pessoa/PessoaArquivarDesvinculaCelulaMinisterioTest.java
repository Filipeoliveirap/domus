package com.domus.api.modules.pessoa;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.CelulaService;
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

/**
 * Regressão de produção: arquivar uma pessoa que é membro de célula/ministério deixava a
 * linha em celula_membro/ministerio_membro apontando pra uma pessoa arquivada — o próximo
 * lazy-load de {@code membro.getPessoa()} (ex.: abrir o detalhe da célula) estourava
 * {@code EntityNotFoundException}, porque @SQLRestriction de Pessoa também vale pra
 * inicialização do proxy, não só pra JOIN.
 */
@SpringBootTest
@Transactional
class PessoaArquivarDesvinculaCelulaMinisterioTest {

    @Autowired PessoaService pessoaService;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired CelulaService celulaService;
    @Autowired MinisterioRepository ministerioRepository;
    @Autowired MinisterioMembroRepository ministerioMembroRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired com.domus.api.modules.evento.inscricao.InscricaoService inscricaoService;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired MovimentacaoContribuinteRepository contribuinteRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Arquivar Membro " + UUID.randomUUID())
                .emailContato("arqmembro-" + UUID.randomUUID() + "@teste.com")
                .build());
    }

    @Test
    void arquivarMembro_desvinculaDeCelulaEMinisterio_semQuebrarOAbrirDaCelula() {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Membro " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());

        Celula celula = celulaRepository.save(Celula.builder().igreja(igreja).nome("Célula " + UUID.randomUUID()).build());
        celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(pessoa).papel(PapelCelula.MEMBRO).build());

        Ministerio ministerio = ministerioRepository.save(Ministerio.builder().igreja(igreja).nome("Rede " + UUID.randomUUID()).build());
        ministerioMembroRepository.save(MinisterioMembro.builder()
                .igreja(igreja).ministerio(ministerio).pessoa(pessoa).papel(Papel.MEMBRO).build());

        // Evento AGENDADO (futuro): arquivar cancela a inscrição — ela sai da vaga.
        Evento eventoFuturo = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento Futuro " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1)).build());
        InscricaoEvento inscricaoFutura = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(eventoFuturo).pessoa(pessoa).status(StatusInscricao.CONFIRMADA).build());
        UUID inscricaoFuturaId = inscricaoFutura.getId();

        // Evento ENCERRADO: ela participou, isso é histórico — arquivar não mexe.
        Evento eventoEncerrado = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento Encerrado " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().minusDays(10))
                .fimEm(LocalDateTime.now().minusDays(9)).build());
        InscricaoEvento inscricaoPassada = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(eventoEncerrado).pessoa(pessoa).status(StatusInscricao.CONFIRMADA).build());
        UUID inscricaoPassadaId = inscricaoPassada.getId();

        CategoriaFinanceira categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Dízimo " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA).build());
        MovimentacaoFinanceira movimentacao = movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());
        MovimentacaoContribuinte contribuicao = contribuinteRepository.save(MovimentacaoContribuinte.builder()
                .movimentacao(movimentacao).pessoa(pessoa).valor(BigDecimal.TEN).build());
        UUID contribuicaoId = contribuicao.getId();
        entityManager.flush();

        pessoaService.arquivarMembro(pessoa.getId(), igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(celulaMembroRepository.findByPessoaId(pessoa.getId())).isEmpty();
        assertThat(ministerioMembroRepository.findByMinisterioIdAndPessoaId(ministerio.getId(), pessoa.getId())).isEmpty();

        // Antes do fix, isto estourava EntityNotFoundException ao resolver membro.getPessoa().
        var detalhe = celulaService.detalhe(celula.getId(), igreja.getId(), null);
        assertThat(detalhe.membros()).isEmpty();

        // Evento futuro: inscrição cancelada, sai da vaga — pessoa continua vinculada (não excluída).
        InscricaoEvento futuraDepois = inscricaoRepository.findById(inscricaoFuturaId).orElseThrow();
        assertThat(futuraDepois.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        assertThat(futuraDepois.getPessoa()).isNotNull();

        // Evento já encerrado: nada muda — ela participou, isso é histórico.
        InscricaoEvento passadaDepois = inscricaoRepository.findById(inscricaoPassadaId).orElseThrow();
        assertThat(passadaDepois.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(passadaDepois.getPessoa()).isNotNull();
        assertThat(passadaDepois.getPessoa().getId()).isEqualTo(pessoa.getId());

        // Lista de participantes do evento encerrado: mostra os dados reais (não "removida"),
        // sem estourar EntityNotFoundException — pessoa está só arquivada, não excluída.
        var participantes = inscricaoService.listarParticipantes(eventoEncerrado.getId(), igreja.getId());
        assertThat(participantes).hasSize(1);
        assertThat(participantes.get(0).nome()).isEqualTo(pessoa.getNome());
        assertThat(participantes.get(0).pessoaId()).isEqualTo(pessoa.getId());

        // Contribuição financeira nunca é "cancelável" — continua vinculada enquanto só arquivada.
        MovimentacaoContribuinte contribuicaoDepois = contribuinteRepository.findById(contribuicaoId).orElseThrow();
        assertThat(contribuicaoDepois.getPessoa()).isNotNull();
        assertThat(contribuicaoDepois.getPessoa().getId()).isEqualTo(pessoa.getId());
    }
}
