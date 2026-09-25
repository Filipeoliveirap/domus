package com.domus.api.modules.contapagar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContaAPagarRepository extends JpaRepository<ContaAPagar, UUID> {

    @Query("SELECT c FROM ContaAPagar c WHERE c.igreja.id = :igrejaId " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:vencimentoAte IS NULL OR c.vencimento <= :vencimentoAte) " +
           "AND (:beneficiarioTexto IS NULL OR LOWER(c.beneficiarioTexto) LIKE LOWER(CONCAT('%', CAST(:beneficiarioTexto AS string), '%')))")
    Page<ContaAPagar> listar(@Param("igrejaId") UUID igrejaId,
                              @Param("status") StatusConta status,
                              @Param("vencimentoAte") LocalDate vencimentoAte,
                              @Param("beneficiarioTexto") String beneficiarioTexto,
                              Pageable pageable);

    Optional<ContaAPagar> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /** Busca geradoras com recorrência ativa que precisam de materialização. */
    @Query("SELECT c FROM ContaAPagar c WHERE c.recorrenciaFrequencia IS NOT NULL " +
           "AND c.deletedAt IS NULL AND c.serie IS NOT NULL AND c.id = c.serie.id")
    List<ContaAPagar> buscarGeradorasAtivas();

    /** Ocorrências de uma série (deletedAt já filtrado pelo @SQLRestriction). */
    List<ContaAPagar> findBySerieIdOrderByVencimentoAsc(UUID serieId);

    /** Conta ocorrências de uma série. */
    long countBySerieId(UUID serieId);

    /** Última ocorrência materializada de uma série. */
    Optional<ContaAPagar> findTopBySerieIdAndDeletedAtIsNullOrderByVencimentoDesc(UUID serieId);

    /** Idempotência: verifica se já existe ocorrência futura com aquele vencimento. */
    boolean existsBySerieIdAndVencimentoAndDeletedAtIsNull(UUID serieId, LocalDate vencimento);

    /** Buscar por id de série + igreja (projeções). */
    Optional<ContaAPagar> findByIdAndSerieIdAndIgrejaId(UUID id, UUID serieId, UUID igrejaId);

    // --- Resumo KPIs (nativos para performance) ---

    @Query(value = "SELECT COALESCE(SUM(valor), 0) FROM conta_a_pagar " +
                   "WHERE igreja_id = :igrejaId AND status = 'EM_ABERTO' " +
                   "AND vencimento = CURRENT_DATE AND deleted_at IS NULL",
           nativeQuery = true)
    BigDecimal sumVenceHoje(@Param("igrejaId") UUID igrejaId);

    @Query(value = "SELECT COALESCE(SUM(valor), 0) FROM conta_a_pagar " +
                   "WHERE igreja_id = :igrejaId AND status = 'EM_ABERTO' " +
                   "AND vencimento BETWEEN :inicioMes AND :fimMes AND deleted_at IS NULL",
           nativeQuery = true)
    BigDecimal sumAVencerNoMes(@Param("igrejaId") UUID igrejaId,
                               @Param("inicioMes") LocalDate inicioMes,
                               @Param("fimMes") LocalDate fimMes);

    @Query(value = "SELECT COALESCE(SUM(valor), 0) FROM conta_a_pagar " +
                   "WHERE igreja_id = :igrejaId AND status = 'EM_ABERTO' " +
                   "AND vencimento < CURRENT_DATE AND deleted_at IS NULL",
           nativeQuery = true)
    BigDecimal sumAtrasadas(@Param("igrejaId") UUID igrejaId);

    @Query(value = "SELECT COALESCE(SUM(valor), 0) FROM conta_a_pagar " +
                   "WHERE igreja_id = :igrejaId AND status = 'PAGA' " +
                   "AND pago_em BETWEEN :inicioMes AND :fimMes AND deleted_at IS NULL",
           nativeQuery = true)
    BigDecimal sumPagasNoMes(@Param("igrejaId") UUID igrejaId,
                             @Param("inicioMes") LocalDate inicioMes,
                             @Param("fimMes") LocalDate fimMes);

    // --- LGPD: desvinculação de pessoa ---

    @Modifying
    @Query(value = "UPDATE conta_a_pagar " +
                   "SET beneficiario_pessoa_id = NULL, " +
                   "    beneficiario_texto = 'Pessoa removida do sistema', " +
                   "    updated_at = NOW() " +
                   "WHERE beneficiario_pessoa_id = :pessoaId AND deleted_at IS NULL",
           nativeQuery = true)
    void desvincularBeneficiario(@Param("pessoaId") UUID pessoaId);
}
