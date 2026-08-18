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
import com.domus.api.modules.foto.FotoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Purga tabela-por-tabela da igreja: uma transação, uma linha de DELETE por tabela, ordem
 *  explícita (nunca ON DELETE CASCADE) — se qualquer passo falhar, tudo desfaz e o job diário
 *  tenta de novo amanhã. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgaIgrejaService {

    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final EventoRepository eventoRepository;
    private final VisitanteRepository visitanteRepository;
    private final LocalEventoRepository localEventoRepository;
    private final CelulaMembroRepository celulaMembroRepository;
    private final CelulaRepository celulaRepository;
    private final MinisterioMembroRepository ministerioMembroRepository;
    private final MinisterioRepository ministerioRepository;
    private final UsuarioCapacidadeRepository usuarioCapacidadeRepository;
    private final FotoRepository fotoRepository;
    private final FotoService fotoService;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PessoaRepository pessoaRepository;

    @Transactional
    public void purgar(UUID igrejaId) {
        log.warn("Iniciando purga definitiva da igreja. igreja_id={}", igrejaId);

        inscricaoRepository.deleteAllByIgrejaId(igrejaId);
        movimentacaoRepository.deleteAllByIgrejaId(igrejaId);
        categoriaRepository.deleteAllByIgrejaId(igrejaId);

        celulaMembroRepository.deleteAllByIgrejaId(igrejaId);
        celulaRepository.deleteAllByIgrejaId(igrejaId);
        ministerioMembroRepository.deleteAllByIgrejaId(igrejaId);
        ministerioRepository.deleteAllByIgrejaId(igrejaId);
        usuarioCapacidadeRepository.deleteAllByUsuarioIgrejaId(igrejaId);

        eventoRepository.deleteAllByIgrejaId(igrejaId);
        visitanteRepository.deleteAllByIgrejaId(igrejaId);
        localEventoRepository.deleteAllByIgrejaId(igrejaId);

        igrejaRepository.limparLogoFoto(igrejaId);
        for (var foto : fotoRepository.findByIgrejaId(igrejaId)) {
            try {
                fotoService.remover(foto.getId());
            } catch (Exception e) {
                log.error("Falha ao remover foto na purga da igreja — seguindo para as demais. foto_id={}, igreja_id={}",
                        foto.getId(), igrejaId, e);
            }
        }

        List<UUID> idsFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId);
        if (!idsFilhas.isEmpty()) {
            igrejaRepository.desvincularFamiliaEmLote(idsFilhas);
            log.info("Igrejas vinculadas desvinculadas da família. igreja_mae_id={}, filhas={}", igrejaId, idsFilhas.size());
        }

        usuarioRepository.deleteAllByIgrejaId(igrejaId);
        pessoaRepository.deleteAllByIgrejaId(igrejaId);
    }
}
