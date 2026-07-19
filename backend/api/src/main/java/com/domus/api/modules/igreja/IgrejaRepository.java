package com.domus.api.modules.igreja;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IgrejaRepository extends JpaRepository<Igreja, UUID> {
    boolean existsByCnpj(String cnpj);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Igreja i WHERE i.id = :id")
    Optional<Igreja> buscarComLock(@Param("id") UUID id);

    Optional<Igreja> findByCodigoVinculo(String codigoVinculo);

    /** "Sou mãe?" é ter pelo menos uma filha — não é possuir um código. */
    boolean existsByIgrejaMaeId(UUID igrejaMaeId);

    List<Igreja> findByIgrejaMaeIdOrderByNomeAsc(UUID igrejaMaeId);

    /** Só os ids das filhas — evita carregar entidade à toa no cálculo da família. */
    @Query("SELECT i.id FROM Igreja i WHERE i.igrejaMae.id = :maeId")
    List<UUID> buscarIdsDasFilhas(@Param("maeId") UUID maeId);
}
