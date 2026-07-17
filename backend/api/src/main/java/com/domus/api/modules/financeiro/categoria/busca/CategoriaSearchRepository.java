package com.domus.api.modules.financeiro.categoria.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoriaSearchRepository extends ElasticsearchRepository<CategoriaDocument, String> {
}