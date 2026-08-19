package com.domus.api.modules.financeiro.movimentacao.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovimentacaoSearchRepository extends ElasticsearchRepository<MovimentacaoDocument, String> {
    void deleteByIgrejaId(String igrejaId);
}