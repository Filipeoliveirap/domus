package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.ministerio.MinisterioMembroRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PurgaIgrejaServiceTest {

    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CategoriaFinanceiraRepository categoriaRepository;
    InscricaoRepository inscricaoRepository;
    EventoRepository eventoRepository;
    VisitanteRepository visitanteRepository;
    LocalEventoRepository localEventoRepository;
    CelulaMembroRepository celulaMembroRepository;
    CelulaRepository celulaRepository;
    MinisterioMembroRepository ministerioMembroRepository;
    MinisterioRepository ministerioRepository;
    UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    FotoRepository fotoRepository;
    FotoService fotoService;
    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    PessoaRepository pessoaRepository;
    PessoaSearchRepository pessoaSearchRepository;
    EventoSearchRepository eventoSearchRepository;
    UsuarioSearchRepository usuarioSearchRepository;
    MovimentacaoSearchRepository movimentacaoSearchRepository;
    CategoriaSearchRepository categoriaSearchRepository;
    CelulaSearchRepository celulaSearchRepository;
    MinisterioSearchRepository ministerioSearchRepository;
    VisitanteSearchRepository visitanteSearchRepository;
    EmailService emailService;
    PurgaIgrejaService service;
    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        eventoRepository = mock(EventoRepository.class);
        visitanteRepository = mock(VisitanteRepository.class);
        localEventoRepository = mock(LocalEventoRepository.class);
        celulaMembroRepository = mock(CelulaMembroRepository.class);
        celulaRepository = mock(CelulaRepository.class);
        ministerioMembroRepository = mock(MinisterioMembroRepository.class);
        ministerioRepository = mock(MinisterioRepository.class);
        usuarioCapacidadeRepository = mock(UsuarioCapacidadeRepository.class);
        fotoRepository = mock(FotoRepository.class);
        fotoService = mock(FotoService.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        pessoaSearchRepository = mock(PessoaSearchRepository.class);
        eventoSearchRepository = mock(EventoSearchRepository.class);
        usuarioSearchRepository = mock(UsuarioSearchRepository.class);
        movimentacaoSearchRepository = mock(MovimentacaoSearchRepository.class);
        categoriaSearchRepository = mock(CategoriaSearchRepository.class);
        celulaSearchRepository = mock(CelulaSearchRepository.class);
        ministerioSearchRepository = mock(MinisterioSearchRepository.class);
        visitanteSearchRepository = mock(VisitanteSearchRepository.class);
        emailService = mock(EmailService.class);
        service = new PurgaIgrejaService(movimentacaoRepository, categoriaRepository, inscricaoRepository,
                eventoRepository, visitanteRepository, localEventoRepository,
                celulaMembroRepository, celulaRepository, ministerioMembroRepository, ministerioRepository,
                usuarioCapacidadeRepository, fotoRepository, fotoService, igrejaRepository,
                usuarioRepository, pessoaRepository,
                pessoaSearchRepository, eventoSearchRepository, usuarioSearchRepository,
                movimentacaoSearchRepository, categoriaSearchRepository, celulaSearchRepository,
                ministerioSearchRepository, visitanteSearchRepository, emailService);
    }

    @Test
    void purgaApagaInscricoesMovimentacoesECategoriasNaOrdemCerta() {
        service.purgar(igrejaId);

        var ordem = inOrder(inscricaoRepository, movimentacaoRepository, categoriaRepository);
        ordem.verify(inscricaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(movimentacaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(categoriaRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaApagaCelulaEMinisterioAntesDosCadastrosPais() {
        service.purgar(igrejaId);

        var ordem = inOrder(celulaMembroRepository, celulaRepository, ministerioMembroRepository, ministerioRepository);
        ordem.verify(celulaMembroRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(celulaRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(ministerioMembroRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(ministerioRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaApagaCapacidadesDeUsuario() {
        service.purgar(igrejaId);

        verify(usuarioCapacidadeRepository).deleteAllByUsuarioIgrejaId(igrejaId);
    }

    @Test
    void purgaApagaEventoVisitanteELocal() {
        service.purgar(igrejaId);

        verify(eventoRepository).deleteAllByIgrejaId(igrejaId);
        verify(visitanteRepository).deleteAllByIgrejaId(igrejaId);
        verify(localEventoRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaRemoveFotosUmaAUmaViaFotoService() {
        UUID fotoId1 = UUID.randomUUID();
        UUID fotoId2 = UUID.randomUUID();
        Foto foto1 = Foto.builder().id(fotoId1).build();
        Foto foto2 = Foto.builder().id(fotoId2).build();
        when(fotoRepository.findByIgrejaId(igrejaId)).thenReturn(List.of(foto1, foto2));

        service.purgar(igrejaId);

        verify(fotoService).remover(fotoId1);
        verify(fotoService).remover(fotoId2);
    }

    @Test
    void purgaDeFotoContinuaMesmoSeUmaFalhar() {
        UUID fotoId1 = UUID.randomUUID();
        UUID fotoId2 = UUID.randomUUID();
        Foto foto1 = Foto.builder().id(fotoId1).build();
        Foto foto2 = Foto.builder().id(fotoId2).build();
        when(fotoRepository.findByIgrejaId(igrejaId)).thenReturn(List.of(foto1, foto2));
        doThrow(new RuntimeException("falha no R2")).when(fotoService).remover(fotoId1);

        service.purgar(igrejaId);

        verify(fotoService).remover(fotoId2);
    }

    @Test
    void purgaApagaUsuarioDepoisPessoa() {
        service.purgar(igrejaId);

        var ordem = inOrder(usuarioRepository, pessoaRepository);
        ordem.verify(usuarioRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(pessoaRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaDesvinculaFilhasQuandoIgrejaEhMae() {
        UUID filha1 = UUID.randomUUID();
        UUID filha2 = UUID.randomUUID();
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of(filha1, filha2));

        service.purgar(igrejaId);

        verify(igrejaRepository).desvincularFamiliaEmLote(List.of(filha1, filha2));
    }

    @Test
    void purgaNaoChamaDesvinculoQuandoIgrejaNaoEhMae() {
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of());

        service.purgar(igrejaId);

        verify(igrejaRepository, never()).desvincularFamiliaEmLote(any());
    }

    @Test
    void purgaApagaDocumentosDeTodosOsIndicesDoElasticsearch() {
        service.purgar(igrejaId);

        String id = igrejaId.toString();
        verify(pessoaSearchRepository).deleteByIgrejaId(id);
        verify(eventoSearchRepository).deleteByIgrejaId(id);
        verify(usuarioSearchRepository).deleteByIgrejaId(id);
        verify(movimentacaoSearchRepository).deleteByIgrejaId(id);
        verify(categoriaSearchRepository).deleteByIgrejaId(id);
        verify(celulaSearchRepository).deleteByIgrejaId(id);
        verify(ministerioSearchRepository).deleteByIgrejaId(id);
        verify(visitanteSearchRepository).deleteByIgrejaId(id);
    }

    @Test
    void purgaNaoTravaSeElasticsearchFalhar() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Igreja X").emailContato("x@x.com").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        doThrow(new RuntimeException("ES fora do ar")).when(pessoaSearchRepository).deleteByIgrejaId(anyString());

        service.purgar(igrejaId);

        String id = igrejaId.toString();
        verify(eventoSearchRepository).deleteByIgrejaId(id);
        verify(usuarioSearchRepository).deleteByIgrejaId(id);
        verify(movimentacaoSearchRepository).deleteByIgrejaId(id);
        verify(categoriaSearchRepository).deleteByIgrejaId(id);
        verify(celulaSearchRepository).deleteByIgrejaId(id);
        verify(ministerioSearchRepository).deleteByIgrejaId(id);
        verify(visitanteSearchRepository).deleteByIgrejaId(id);
        verify(emailService).enviar(eq("x@x.com"), anyString(), anyString());
        verify(igrejaRepository).deleteById(igrejaId);
    }

    @Test
    void purgaEnviaEmailFinalAntesDeApagarALinhaDaIgreja() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Igreja X").emailContato("x@x.com").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        service.purgar(igrejaId);

        var ordem = inOrder(emailService, igrejaRepository);
        ordem.verify(emailService).enviar(eq("x@x.com"), contains("excluída"), anyString());
        ordem.verify(igrejaRepository).deleteById(igrejaId);
    }
}
