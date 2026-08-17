package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaRepository extends JpaRepository<Celula, UUID> {

    List<Celula> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    Optional<Celula> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM celula WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    /** @SQLRestriction esconde arquivados de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM celula
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY nome ASC
        """, nativeQuery = true)
    List<Celula> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    /**
     * Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivados também — usado por
     * excluirDefinitivo, que precisa ser chamável tanto na listagem normal (sem vínculo,
     * nunca foi arquivada) quanto na tela de Arquivados (já arquivada).
     */
    @Query(value = "SELECT * FROM celula WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<Celula> findByIdAndIgrejaIdIncluindoArquivadas(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Retorna 0 se o id não pertence a essa igreja — nunca confiar em "id" sozinho. */
    @Modifying
    @Query(value = "UPDATE celula SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);
}
