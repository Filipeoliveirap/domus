package com.domus.api.modules.usuario.busca;

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
public class BuscaUsuarioService {

    private final ElasticsearchOperations elasticsearchOperations;

    public List<ResultadoBusca> buscar(String termo, UUID igrejaId, int limite) {
        Query filtroIgreja = Query.of(q -> q
                .term(t -> t.field("igrejaId").value(igrejaId.toString()))
        );

        Query buscaTexto = Query.of(q -> q
                .multiMatch(m -> m
                        .query(termo)
                        .fields("nome", "email", "role")
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

        SearchHits<UsuarioDocument> hits = elasticsearchOperations.search(nativeQuery, UsuarioDocument.class);

        return hits.stream()
                .map(hit -> {
                    UsuarioDocument doc = hit.getContent();
                    return new ResultadoBusca(
                            doc.getId(),
                            TipoEntidadeOutbox.USUARIO,
                            doc.getNome(),
                            doc.getRole()
                    );
                })
                .toList();
    }
}