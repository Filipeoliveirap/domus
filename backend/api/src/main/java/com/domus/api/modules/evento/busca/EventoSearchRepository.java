package com.domus.api.modules.evento.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoSearchRepository extends ElasticsearchRepository<EventoDocument, String> {
    void deleteByIgrejaId(String igrejaId);
}
