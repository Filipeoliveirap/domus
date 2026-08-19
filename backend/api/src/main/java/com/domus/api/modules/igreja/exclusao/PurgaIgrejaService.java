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
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import com.domus.api.shared.email.EmailService;
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
    private final PessoaSearchRepository pessoaSearchRepository;
    private final EventoSearchRepository eventoSearchRepository;
    private final UsuarioSearchRepository usuarioSearchRepository;
    private final MovimentacaoSearchRepository movimentacaoSearchRepository;
    private final CategoriaSearchRepository categoriaSearchRepository;
    private final CelulaSearchRepository celulaSearchRepository;
    private final MinisterioSearchRepository ministerioSearchRepository;
    private final VisitanteSearchRepository visitanteSearchRepository;
    private final EmailService emailService;

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

        List<UUID> idsFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId);
        if (!idsFilhas.isEmpty()) {
            igrejaRepository.desvincularFamiliaEmLote(idsFilhas);
            log.info("Igrejas vinculadas desvinculadas da família. igreja_mae_id={}, filhas={}", igrejaId, idsFilhas.size());
        }

        // Antes de apagar os usuários, zera as FKs de `igreja` que apontam pra eles
        // (atualizado_por, vinculado_por, exclusao_agendada_por) — senão o DELETE viola a FK.
        igrejaRepository.limparReferenciasDeUsuario(igrejaId);

        usuarioRepository.deleteAllByIgrejaId(igrejaId);
        pessoaRepository.deleteAllByIgrejaId(igrejaId);

        // Fotos por último entre os passos de banco: pessoa.foto_id é ON DELETE RESTRICT,
        // então só dá pra apagar a foto depois que a pessoa (e a igreja/logo) já sumiram.
        igrejaRepository.limparLogoFoto(igrejaId);
        for (var foto : fotoRepository.findByIgrejaId(igrejaId)) {
            fotoService.remover(foto.getId());
        }

        String idTexto = igrejaId.toString();
        for (Runnable limpezaIndice : List.<Runnable>of(
                () -> pessoaSearchRepository.deleteByIgrejaId(idTexto),
                () -> eventoSearchRepository.deleteByIgrejaId(idTexto),
                () -> usuarioSearchRepository.deleteByIgrejaId(idTexto),
                () -> movimentacaoSearchRepository.deleteByIgrejaId(idTexto),
                () -> categoriaSearchRepository.deleteByIgrejaId(idTexto),
                () -> celulaSearchRepository.deleteByIgrejaId(idTexto),
                () -> ministerioSearchRepository.deleteByIgrejaId(idTexto),
                () -> visitanteSearchRepository.deleteByIgrejaId(idTexto)
        )) {
            try {
                limpezaIndice.run();
            } catch (Exception e) {
                log.error("Falha ao limpar índice do Elasticsearch na purga da igreja — seguindo. igreja_id={}", igrejaId, e);
            }
        }

        igrejaRepository.findById(igrejaId).ifPresent(igreja ->
                emailService.enviar(igreja.getEmailContato(), "Sua igreja foi excluída",
                        "A exclusão definitiva de \"" + igreja.getNome() + "\" foi concluída. Todos os dados foram removidos."));

        igrejaRepository.deleteById(igrejaId);
        log.warn("Purga definitiva da igreja concluída. igreja_id={}", igrejaId);
    }
}
