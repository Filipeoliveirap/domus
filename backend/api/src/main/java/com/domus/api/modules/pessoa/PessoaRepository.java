package com.domus.api.modules.pessoa;

import com.domus.api.modules.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    @Query("""
        SELECT m FROM Pessoa m
        WHERE m.igreja.id = :igrejaId
          AND (
            :q IS NULL
            OR LOWER(m.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(m.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR m.telefone LIKE CONCAT('%', CAST(:q AS string), '%')
          )
          AND (:vinculo IS NULL OR m.vinculo = :vinculo)
        """)
    Page<Pessoa> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                 @Param("q") String q,
                                 @Param("vinculo") Vinculo vinculo,
                                 Pageable pageable);

    Optional<Pessoa> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    boolean existsByEmail(String email);

    /** Bairros distintos já cadastrados na igreja — alimenta a sugestão (datalist) do form. */
    @Query("""
        SELECT DISTINCT m.endereco.bairro FROM Pessoa m
        WHERE m.igreja.id = :igrejaId AND m.endereco.bairro IS NOT NULL
        ORDER BY m.endereco.bairro
        """)
    java.util.List<String> bairrosDistintos(@Param("igrejaId") UUID igrejaId);

    @Query(value = """
    SELECT COUNT(*) > 0 FROM pessoa
    WHERE LOWER(email) = LOWER(:email)
    """, nativeQuery = true)
    boolean existsByEmailIncluindoArquivados(@Param("email") String email);

    /** Aniversariantes de um mês (1-12), ordenados por dia. Para a tela de início. */
    @Query("""
        SELECT m FROM Pessoa m
        WHERE m.igreja.id = :igrejaId
          AND m.dataNascimento IS NOT NULL
          AND EXTRACT(MONTH FROM m.dataNascimento) = :mes
        ORDER BY EXTRACT(DAY FROM m.dataNascimento)
        """)
    java.util.List<Pessoa> aniversariantesDoMes(@Param("igrejaId") UUID igrejaId, @Param("mes") int mes);

    /**
     * Todos os membros da igreja com telefone preenchido — usado por
     * {@code PessoaService.avisoTelefoneDuplicado} (B2) para comparar por dígitos normalizados
     * em Java (o telefone chega formatado de jeitos diferentes; normalizar isso em SQL de forma
     * portável não vale a pena para o tamanho de igreja que este produto atende).
     */
    java.util.List<Pessoa> findByIgrejaIdAndTelefoneIsNotNull(UUID igrejaId);

    long countByIgrejaId(UUID igrejaId);

    long countByIgrejaIdAndCreatedAtAfter(UUID igrejaId, java.time.LocalDateTime desde);

    /**
     * Solta a referência de {@code pessoa.foto_id} antes de apagar a foto de verdade.
     *
     * <p>{@code Pessoa} tem {@code @SQLRestriction("deleted_at IS NULL")}, então um
     * {@code UPDATE} via JPQL não enxergaria pessoa arquivada — exatamente quem
     * {@link com.domus.api.modules.foto.LimpezaFotosJob#limparDeArquivadas} precisa
     * desvincular. Por isso é nativa, mesmo padrão de
     * {@link com.domus.api.modules.foto.FotoRepository#buscarDeArquivadas}. Sem isto, o
     * {@code DELETE FROM foto} seguinte é recusado pelo {@code ON DELETE RESTRICT}.
     */
    @Modifying
    @Query(value = "UPDATE pessoa SET foto_id = NULL WHERE foto_id = :fotoId", nativeQuery = true)
    void desvincularFoto(@Param("fotoId") UUID fotoId);
}