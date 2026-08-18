package com.domus.api.modules.celula.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CelulaSearchRepository extends ElasticsearchRepository<CelulaDocument, String> {
    void deleteByIgrejaId(String igrejaId);
}
