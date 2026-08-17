package com.domus.api.modules.sync;

import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.modules.visitante.busca.VisitanteDocument;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisitanteSincronizador implements SincronizadorEntidade {

    private final VisitanteRepository visitanteRepository;
    private final VisitanteSearchRepository visitanteSearchRepository;
    private final CelulaMembroRepository celulaMembroRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.VISITANTE;
    }

    @Override
    public void indexar(UUID entidadeId) {
        visitanteRepository.findById(entidadeId).ifPresentOrElse(
                visitante -> {
                    // Visitante convertido virou pessoa: já é indexado como PESSOA, não
                    // deve mais aparecer na busca como VISITANTE.
                    if (visitante.getConvertidoPessoaId() != null) {
                        visitanteSearchRepository.deleteById(entidadeId.toString());
                        log.debug("Visitante convertido em pessoa, removido do Elastic. id={}", entidadeId);
                        return;
                    }
                    var celulaId = celulaMembroRepository.findByVisitanteId(entidadeId)
                            .map(cm -> cm.getCelula().getId())
                            .orElse(null);
                    visitanteSearchRepository.save(VisitanteDocument.de(visitante, celulaId));
                    log.debug("Visitante indexado no Elastic. id={}", entidadeId);
                },
                () -> {
                    visitanteSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Visitante não encontrado no Postgres, removido do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        visitanteSearchRepository.deleteById(entidadeId.toString());
        log.debug("Visitante removido do Elastic. id={}", entidadeId);
    }
}
