package com.domus.api.modules.pessoa.busca;

import com.domus.api.modules.pessoa.busca.PessoaDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PessoaSearchRepository extends ElasticsearchRepository<PessoaDocument, String> {
}
