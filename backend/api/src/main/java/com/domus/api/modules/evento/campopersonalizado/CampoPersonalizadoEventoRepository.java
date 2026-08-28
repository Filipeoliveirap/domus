package com.domus.api.modules.evento.campopersonalizado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampoPersonalizadoEventoRepository extends JpaRepository<CampoPersonalizadoEvento, UUID> {

    List<CampoPersonalizadoEvento> findByEventoIdAndIgrejaIdOrderByOrdemAsc(UUID eventoId, UUID igrejaId);

    Optional<CampoPersonalizadoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga campo arquivado também (nativa,
     *  bypassa o {@code @SQLRestriction}) — usado quando a resposta guardada é um snapshot
     *  e precisa exibir a pergunta mesmo que o admin já tenha removido o campo depois. */
    @Query(value = "SELECT * FROM campo_personalizado_evento WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<CampoPersonalizadoEvento> findByIdAndIgrejaIdIncluindoArquivados(
            @Param("id") UUID id, @Param("igrejaId") UUID igrejaId);
}
