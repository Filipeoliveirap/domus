package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.PapelCelula;
import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.AcompanhanteInscricao;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinte;
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
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste mais importante do projeto (conforme a spec de exclusão de igreja): cria uma igreja de
 * teste com um registro de CADA tipo de entidade do domínio (pessoa, usuário, evento+local,
 * inscrição+acompanhante, categoria+movimentação+contribuinte, célula+membro, ministério+membro,
 * visitante) e roda a purga de verdade (PurgaIgrejaService.purgar) contra o banco real, provando
 * que a ordem de DELETE explícita não bate em nenhuma FK e que tudo some.
 */
@SpringBootTest
@Transactional
class PurgaIgrejaIntegrationTest {

    @Autowired PurgaIgrejaService purgaIgrejaService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired LocalEventoRepository localEventoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired MinisterioRepository ministerioRepository;
    @Autowired MinisterioMembroRepository ministerioMembroRepository;
    @Autowired VisitanteRepository visitanteRepository;
    @Autowired EntityManager entityManager;

    @Test
    void purgaTudoSemErroDeFkComUmRegistroDeCadaTipo() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja de Teste da Purga").emailContato("purga@teste.com").build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano de Teste").email("fulano-purga@teste.com")
                .vinculo(Vinculo.MEMBRO).build());

        Role roleAdmin = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(roleAdmin).senhaHash("hash").ativo(true).build());

        LocalEvento local = localEventoRepository.save(LocalEvento.builder()
                .igreja(igreja).nome("Salão de Teste").build());

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento de Teste").local(local)
                .inicioEm(LocalDateTime.now().plusDays(1)).build());

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(pessoa).status(StatusInscricao.CONFIRMADA).build();
        inscricao.getAcompanhantes().add(AcompanhanteInscricao.builder()
                .inscricao(inscricao).nome("Acompanhante de Teste").build());
        inscricao = inscricaoRepository.save(inscricao);

        CategoriaFinanceira categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Categoria de Teste").tipo(TipoCategoria.ENTRADA).build());

        MovimentacaoFinanceira movimentacao = MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build();
        movimentacao.getContribuintes().add(MovimentacaoContribuinte.builder()
                .movimentacao(movimentacao).pessoa(pessoa).valor(BigDecimal.TEN).build());
        movimentacao = movimentacaoRepository.save(movimentacao);

        Celula celula = celulaRepository.save(Celula.builder()
                .igreja(igreja).nome("Célula de Teste").build());
        celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(pessoa).papel(PapelCelula.LIDER).build());

        Ministerio ministerio = ministerioRepository.save(Ministerio.builder()
                .igreja(igreja).nome("Ministério de Teste").build());
        ministerioMembroRepository.save(MinisterioMembro.builder()
                .igreja(igreja).ministerio(ministerio).pessoa(pessoa).papel(Papel.LIDER).build());

        Visitante visitante = visitanteRepository.save(Visitante.builder()
                .igreja(igreja).nome("Visitante de Teste").build());

        entityManager.flush();
        entityManager.clear();

        purgaIgrejaService.purgar(igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(igrejaRepository.findById(igreja.getId())).isEmpty();
        assertThat(pessoaRepository.findByIdAndIgrejaId(pessoa.getId(), igreja.getId())).isEmpty();
        assertThat(usuarioRepository.findByIdAndIgrejaId(usuario.getId(), igreja.getId())).isEmpty();
        assertThat(eventoRepository.findByIdAndIgrejaId(evento.getId(), igreja.getId())).isEmpty();
        assertThat(inscricaoRepository.findByIdAndIgrejaId(inscricao.getId(), igreja.getId())).isEmpty();
        assertThat(localEventoRepository.findByIdAndIgrejaId(local.getId(), igreja.getId())).isEmpty();
        assertThat(categoriaRepository.findByIdAndIgrejaId(categoria.getId(), igreja.getId())).isEmpty();
        assertThat(movimentacaoRepository.findById(movimentacao.getId())).isEmpty();
        assertThat(celulaRepository.findByIdAndIgrejaId(celula.getId(), igreja.getId())).isEmpty();
        assertThat(ministerioRepository.findByIdAndIgrejaId(ministerio.getId(), igreja.getId())).isEmpty();
        assertThat(visitanteRepository.findByIdAndIgrejaId(visitante.getId(), igreja.getId())).isEmpty();
    }
}
