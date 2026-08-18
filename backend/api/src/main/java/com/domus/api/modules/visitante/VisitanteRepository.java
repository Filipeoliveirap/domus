package com.domus.api.modules.visitante;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VisitanteRepository extends JpaRepository<Visitante, UUID> {

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<Visitante> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT v FROM Visitante v
        WHERE v.igreja.id = :igrejaId
          AND (
            :q IS NULL
            OR LOWER(v.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR v.telefone LIKE CONCAT('%', CAST(:q AS string), '%')
          )
          AND (:contato IS NULL OR v.contatoRealizado = :contato)
          AND (:visita IS NULL OR v.visitaRealizada = :visita)
          AND (:acompanhamento IS NULL OR v.acompanhamentoFeito = :acompanhamento)
          AND v.convertidoPessoaId IS NULL
          AND NOT EXISTS (
            SELECT cm FROM CelulaMembro cm WHERE cm.visitante.id = v.id
          )
        """)
    Page<Visitante> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                    @Param("q") String q,
                                    @Param("contato") Boolean contatoRealizado,
                                    @Param("visita") Boolean visitaRealizada,
                                    @Param("acompanhamento") Boolean acompanhamentoFeito,
                                    Pageable pageable);

    @Query("SELECT COUNT(cm) > 0 FROM CelulaMembro cm WHERE cm.visitante.id = :visitanteId")
    boolean existeCelulaMembroAtivo(@Param("visitanteId") UUID visitanteId);

    /** Mantém o registro de que o visitante converteu — só marca a pessoa resultante como removida. */
    @Modifying
    @Query(value = """
        UPDATE visitante
           SET convertido_pessoa_id = NULL, convertido_pessoa_removida = TRUE
         WHERE convertido_pessoa_id = :pessoaId
        """, nativeQuery = true)
    void desvincularConvertido(@Param("pessoaId") UUID pessoaId);

    @Modifying
    @Query(value = """
        UPDATE visitante
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);
}
