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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
        service = new PurgaIgrejaService(movimentacaoRepository, categoriaRepository, inscricaoRepository,
                eventoRepository, visitanteRepository, localEventoRepository,
                celulaMembroRepository, celulaRepository, ministerioMembroRepository, ministerioRepository,
                usuarioCapacidadeRepository);
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
}
