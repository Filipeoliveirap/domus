package com.domus.api.modules.foto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoRepository extends JpaRepository<Foto, UUID> {

    Optional<Foto> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /**
     * Fotos órfãs: mais velhas que o corte e que NENHUMA das três tabelas referencia.
     *
     * <p>Acontece quando alguém envia a foto e abandona o formulário sem salvar. Sem esta
     * limpeza, todo formulário abandonado deixa lixo permanente no bucket.
     */
    @Query("""
        SELECT f FROM Foto f
        WHERE f.createdAt < :corte
          AND NOT EXISTS (SELECT 1 FROM Pessoa p WHERE p.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Evento e WHERE e.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Igreja i WHERE i.logoFoto = f)
    """)
    List<Foto> buscarOrfas(@Param("corte") LocalDateTime corte);
}
