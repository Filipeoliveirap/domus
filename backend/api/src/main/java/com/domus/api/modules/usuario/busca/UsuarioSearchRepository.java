package com.domus.api.modules.usuario.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioSearchRepository extends ElasticsearchRepository<UsuarioDocument, String> {
}