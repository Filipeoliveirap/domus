package com.domus.api.modules.ministerio.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MinisterioSearchRepository extends ElasticsearchRepository<MinisterioDocument, String> {
}
