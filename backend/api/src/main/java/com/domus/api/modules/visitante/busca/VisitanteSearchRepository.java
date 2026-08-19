package com.domus.api.modules.visitante.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitanteSearchRepository extends ElasticsearchRepository<VisitanteDocument, String> {
    void deleteByIgrejaId(String igrejaId);
}
