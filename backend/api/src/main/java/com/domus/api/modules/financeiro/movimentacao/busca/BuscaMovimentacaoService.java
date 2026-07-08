package com.domus.api.modules.financeiro.movimentacao.busca;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.shared.DTO.ResultadoBusca;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuscaMovimentacaoService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ResultadoBusca> buscar(String termo, UUID igrejaId, int limite) {
        Query filtroIgreja = Query.of(q -> q
                .term(t -> t.field("igrejaId").value(igrejaId.toString()))
        );

        Query buscaTexto = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("descricao", "categoriaNome", "membroNome")
                        .fuzziness("AUTO")
                )
        );

        Query queryFinal = Query.of(q -> q
                .bool(b -> b
                        .filter(filtroIgreja)
                        .must(buscaTexto)
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(queryFinal)
                .withMaxResults(limite)
                .build();

        SearchHits<MovimentacaoDocument> hits = elasticsearchOperations.search(nativeQuery, MovimentacaoDocument.class);

        return hits.stream()
                .map(hit -> {
                    MovimentacaoDocument doc = hit.getContent();
                    String titulo = doc.getDescricao() != null && !doc.getDescricao().isBlank()
                            ? doc.getDescricao()
                            : doc.getCategoriaNome();
                    return new ResultadoBusca(
                            doc.getId(),
                            TipoEntidadeOutbox.MOVIMENTACAO,
                            titulo,
                            doc.getCategoriaNome()
                    );
                })
                .toList();
    }
}