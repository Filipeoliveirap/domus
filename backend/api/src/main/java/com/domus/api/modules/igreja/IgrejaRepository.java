package com.domus.api.modules.igreja;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IgrejaRepository extends JpaRepository<Igreja, UUID> {
    boolean existsByCnpj(String cnpj);
}
