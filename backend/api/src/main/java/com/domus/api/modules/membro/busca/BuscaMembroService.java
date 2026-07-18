package com.domus.api.modules.membro.busca;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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
public class BuscaMembroService {

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

        Query fuzzyOutros = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("email^2", "ministerio")
                        .fuzziness("AUTO")
                )
        );

        Query prefixo = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("nome^3", "email^2", "telefone", "ministerio")
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BoolPrefix)
                        .boost(2.0f)
                )
        );

        Query queryFinal = Query.of(q -> q
                .bool(b -> b
                        .filter(filtroIgreja)
                        .should(fuzzyNome)
                        .should(fuzzyOutros)
                        .should(prefixo)
                        .minimumShouldMatch("1")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(queryFinal)
                .withMaxResults(limite)
                .build();

        SearchHits<MembroDocument> hits = elasticsearchOperations.search(nativeQuery, MembroDocument.class);

        return hits.stream()
                .map(hit -> {
                    MembroDocument doc = hit.getContent();
                    return new ResultadoBusca(
                            doc.getId(),
                            TipoEntidadeOutbox.MEMBRO,
                            doc.getNome(),
                            doc.getEmail() != null ? doc.getEmail() : doc.getMinisterio()
                    );
                })
                .toList();
    }
}