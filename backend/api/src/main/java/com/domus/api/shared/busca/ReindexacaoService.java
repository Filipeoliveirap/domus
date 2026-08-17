package com.domus.api.shared.busca;

import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.busca.CelulaDocument;
import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.busca.EventoDocument;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaDocument;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoDocument;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.ministerio.busca.MinisterioDocument;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.busca.PessoaDocument;
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.usuario.busca.UsuarioDocument;
import com.domus.api.modules.usuario.busca.UsuarioSearchRepository;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.modules.visitante.busca.VisitanteDocument;
import com.domus.api.modules.visitante.busca.VisitanteSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReindexacaoService {

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * Recria o índice (delete + create com o mapping/settings atuais do Document).
     * Necessário quando o analyzer muda: mapping de índice é imutável, então re-salvar
     * num índice antigo não aplicaria o novo analyzer (asciifolding/edge_ngram).
     */
    private void recriarIndice(Class<?> docClass) {
        IndexOperations ops = elasticsearchOperations.indexOps(docClass);
        if (ops.exists()) {
            ops.delete();
        }
        ops.createWithMapping();
    }

    private final PessoaRepository pessoaRepository;
    private final PessoaSearchRepository pessoaSearchRepository;
    private final EventoRepository eventoRepository;
    private final EventoSearchRepository eventoSearchRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioSearchRepository usuarioSearchRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final MovimentacaoSearchRepository  movimentacaoSearchRepository;
    private final CategoriaSearchRepository  categoriaSearchRepository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final CelulaRepository celulaRepository;
    private final CelulaSearchRepository celulaSearchRepository;
    private final VisitanteRepository visitanteRepository;
    private final VisitanteSearchRepository visitanteSearchRepository;
    private final CelulaMembroRepository celulaMembroRepository;
    private final MinisterioRepository ministerioRepository;
    private final MinisterioSearchRepository ministerioSearchRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> reindexarTudo() {
        Map<String, Long> resultado = new HashMap<>();

        resultado.put("pessoas", reindexarPessoas());
        resultado.put("eventos", reindexarEventos());
        resultado.put("usuarios", reindexarUsuarios());
        resultado.put("movimentacoes", reindexarMovimentacoes());
        resultado.put("categorias", reindexarCategorias());
        resultado.put("celulas", reindexarCelulas());
        resultado.put("visitantes", reindexarVisitantes());
        resultado.put("ministerios", reindexarMinisterios());

        log.info("Reindexação completa. resultado={}", resultado);
        return resultado;
    }

    private long reindexarPessoas() {
        recriarIndice(PessoaDocument.class);
        var docs = pessoaRepository.findAll().stream()
                .map(PessoaDocument::de)
                .toList();
        pessoaSearchRepository.saveAll(docs);
        log.info("Reindexadas {} pessoas.", docs.size());
        return docs.size();
    }

    private long reindexarEventos() {
        recriarIndice(EventoDocument.class);
        var docs = eventoRepository.findAll().stream()
                .map(EventoDocument::de)
                .toList();
        eventoSearchRepository.saveAll(docs);
        log.info("Reindexados {} eventos.", docs.size());
        return docs.size();
    }

    private long reindexarUsuarios() {
        recriarIndice(UsuarioDocument.class);
        var docs = usuarioRepository.findAll().stream()
                .map(UsuarioDocument::de)
                .toList();
        usuarioSearchRepository.saveAll(docs);
        log.info("Reindexados {} usuários.", docs.size());
        return docs.size();
    }

    private long reindexarMovimentacoes() {
        recriarIndice(MovimentacaoDocument.class);
        // Uma movimentação com FK de categoria quebrada (categoria apagada por fora do
        // fluxo normal) não pode derrubar a reindexação de todo o sistema — pula só ela.
        var docs = movimentacaoRepository.findAll().stream()
                .<MovimentacaoDocument>mapMulti((m, consumer) -> {
                    try {
                        consumer.accept(MovimentacaoDocument.de(m));
                    } catch (jakarta.persistence.EntityNotFoundException e) {
                        log.warn("Movimentação {} com categoria inexistente, pulando na reindexação.",
                                m.getId());
                    }
                })
                .toList();
        movimentacaoSearchRepository.saveAll(docs);
        log.info("Reindexadas {} movimentações.", docs.size());
        return docs.size();
    }

    private long reindexarCategorias() {
        recriarIndice(CategoriaDocument.class);
        var docs = categoriaRepository.findAll().stream()
                .map(CategoriaDocument::de)
                .toList();
        categoriaSearchRepository.saveAll(docs);
        log.info("Reindexadas {} categorias.", docs.size());
        return docs.size();
    }

    private long reindexarCelulas() {
        recriarIndice(CelulaDocument.class);
        var docs = celulaRepository.findAll().stream()
                .map(CelulaDocument::de)
                .toList();
        celulaSearchRepository.saveAll(docs);
        log.info("Reindexadas {} células.", docs.size());
        return docs.size();
    }

    private long reindexarVisitantes() {
        recriarIndice(VisitanteDocument.class);
        var celulaIdPorVisitanteId = new java.util.HashMap<java.util.UUID, java.util.UUID>();
        for (Object[] par : celulaMembroRepository.buscarCelulaIdPorVisitanteId()) {
            celulaIdPorVisitanteId.put((java.util.UUID) par[0], (java.util.UUID) par[1]);
        }
        // Visitante convertido em pessoa já é indexado como PESSOA; não indexa de novo.
        var docs = visitanteRepository.findAll().stream()
                .filter(v -> v.getConvertidoPessoaId() == null)
                .map(v -> VisitanteDocument.de(v, celulaIdPorVisitanteId.get(v.getId())))
                .toList();
        visitanteSearchRepository.saveAll(docs);
        log.info("Reindexados {} visitantes.", docs.size());
        return docs.size();
    }

    private long reindexarMinisterios() {
        recriarIndice(MinisterioDocument.class);
        var docs = ministerioRepository.findAll().stream()
                .map(MinisterioDocument::de)
                .toList();
        ministerioSearchRepository.saveAll(docs);
        log.info("Reindexados {} ministérios.", docs.size());
        return docs.size();
    }
}