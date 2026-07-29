package com.domus.api.modules.evento.busca;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.shared.DTO.ResultadoBusca;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BuscaEventoService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ResultadoBusca> buscar(String termo, UUID igrejaId, Set<UUID> idsFamilia, int limite) {
        List<String> idsFamiliaStr = idsFamilia.stream().map(UUID::toString).toList();

        Query filtroVisibilidade = Query.of(q -> q
                .bool(b -> b
                        .should(s -> s.term(t -> t.field("igrejaId").value(igrejaId.toString())))
                        .should(s -> s.bool(bb -> bb
                                .filter(f -> f.terms(t -> t.field("igrejaId")
                                        .terms(tt -> tt.value(idsFamiliaStr.stream()
                                                .map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList()))))
                                .filter(f -> f.term(t -> t.field("restritoPropriaIgreja").value(false)))
                        ))
                        .minimumShouldMatch("1")
                )
        );

        Query fuzzyTitulo = Query.of(q -> q
                .match(m -> m
                        .field("titulo")
                        .query(termo)
                        .fuzziness("AUTO")
                        .prefixLength(1)
                )
        );

        Query fuzzyOutros = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("descricao", "local")
                        .fuzziness("AUTO")
                )
        );

        Query prefixo = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("titulo^3", "descricao", "local")
                        .type(TextQueryType.BoolPrefix)
                        .boost(2.0f)
                )
        );

        Query queryFinal = Query.of(q -> q
                .bool(b -> b
                        .filter(filtroVisibilidade)
                        .should(fuzzyTitulo)
                        .should(fuzzyOutros)
                        .should(prefixo)
                        .minimumShouldMatch("1")
                )
        );

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(queryFinal)
                .withMaxResults(limite)
                .build();

        SearchHits<EventoDocument> hits = elasticsearchOperations.search(nativeQuery, EventoDocument.class);

        return hits.stream()
                .map(hit -> {
                    EventoDocument doc = hit.getContent();
                    return new ResultadoBusca(
                            doc.getId(),
                            TipoEntidadeOutbox.EVENTO,
                            doc.getTitulo(),
                            doc.getLocal() != null ? doc.getLocal() : ""
                    );
                })
                .toList();
    }
}