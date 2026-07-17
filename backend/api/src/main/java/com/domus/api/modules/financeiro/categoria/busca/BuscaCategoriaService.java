package com.domus.api.modules.financeiro.categoria.busca;

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
public class BuscaCategoriaService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ResultadoBusca> buscar(String termo, UUID igrejaId, int limite) {
        Query filtroIgreja = Query.of(q -> q
                .term(t -> t.field("igrejaId").value(igrejaId.toString()))
        );

        Query fuzzyNome = Query.of(q -> q
                .match(m -> m
                        .field("nome")
                        .query(termo)
                        .fuzziness("AUTO")
                        .prefixLength(1)
                )
        );

        Query prefixo = Query.of(q -> q
                .match(m -> m
                        .field("nome")
                        .query(termo)
                )
        );

        Query queryFinal = Query.of(q -> q
                .bool(b -> b
                        .filter(filtroIgreja)
                        .should(fuzzyNome)
                        .should(prefixo)
                        .minimumShouldMatch("1")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(queryFinal)
                .withMaxResults(limite)
                .build();

        SearchHits<CategoriaDocument> hits = elasticsearchOperations.search(nativeQuery, CategoriaDocument.class);

        return hits.stream()
                .map(hit -> {
                    CategoriaDocument doc = hit.getContent();
                    String subtitulo = "ENTRADA".equals(doc.getTipo()) ? "Entrada"
                            : "SAIDA".equals(doc.getTipo()) ? "Saída" : "Ambos";
                    return new ResultadoBusca(
                            doc.getId(),
                            TipoEntidadeOutbox.CATEGORIA,
                            doc.getNome(),
                            subtitulo
                    );
                })
                .toList();
    }
}