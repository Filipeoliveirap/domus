package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.ministerio.MinisterioMembroRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CelulaMembroRepository celulaMembroRepository;
    private final CelulaRepository celulaRepository;
    private final MinisterioMembroRepository ministerioMembroRepository;
    private final MinisterioRepository ministerioRepository;
    private final UsuarioCapacidadeRepository usuarioCapacidadeRepository;

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
    }
}
