package com.domus.api.modules.membro.busca;

import com.domus.api.modules.membro.busca.MembroDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembroSearchRepository extends ElasticsearchRepository<MembroDocument, String> {
}
