package com.domus.api.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracaoPlataformaRepository extends JpaRepository<ConfiguracaoPlataforma, String> {
}
