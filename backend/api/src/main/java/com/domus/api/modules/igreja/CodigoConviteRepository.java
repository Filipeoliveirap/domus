package com.domus.api.modules.igreja;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodigoConviteRepository extends JpaRepository<CodigoConviteCongregacao, Long> {
    Optional<CodigoConviteCongregacao> findByCodigo(String codigo);
    Optional<CodigoConviteCongregacao> findByCodigoAndUsadoEmIsNull(String codigo);
}
