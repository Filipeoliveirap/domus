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

    /** Comparação por dígitos normalizados é feita em Java (PessoaService); não vale normalizar em SQL para este porte. */
    java.util.List<Pessoa> findByIgrejaIdAndTelefoneIsNotNull(UUID igrejaId);

    long countByIgrejaId(UUID igrejaId);

    long countByIgrejaIdAndCreatedAtAfter(UUID igrejaId, java.time.LocalDateTime desde);

    /** Nativa: JPQL não enxergaria pessoa arquivada por causa do @SQLRestriction; sem desvincular, o DELETE em foto cai no ON DELETE RESTRICT. */
    @Modifying
    @Query(value = "UPDATE pessoa SET foto_id = NULL WHERE foto_id = :fotoId", nativeQuery = true)
    void desvincularFoto(@Param("fotoId") UUID fotoId);

    @Modifying
    @Query(value = "DELETE FROM pessoa WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    /** @SQLRestriction esconde arquivadas de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM pessoa
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY nome ASC
        """, nativeQuery = true)
    java.util.List<Pessoa> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivadas também. */
    @Query(value = "SELECT * FROM pessoa WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<Pessoa> findByIdAndIgrejaIdIncluindoArquivadas(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Em lote, pra resolver nome/foto de inscritos/contribuintes sem cair no lazy-load
     *  restrito — pessoa arquivada (mas não excluída) precisa continuar mostrando os dados
     *  reais. Sem igrejaId: os ids já vêm de uma consulta tenant-scoped (inscrição/contribuinte). */
    @Query(value = "SELECT * FROM pessoa WHERE id IN (:ids)", nativeQuery = true)
    java.util.List<Pessoa> findByIdInIncluindoArquivadas(@Param("ids") java.util.List<UUID> ids);

    /** Retorna 0 se o id não pertence a essa igreja — nunca confiar em "id" sozinho. */
    @Modifying
    @Query(value = "UPDATE pessoa SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Pra decidir se a exclusão definitiva pede confirmação simples ou crítica. */
    @Query(value = """
        SELECT
            EXISTS(SELECT 1 FROM usuario WHERE pessoa_id = :id) AS temUsuario,
            EXISTS(SELECT 1 FROM movimentacao_contribuinte WHERE pessoa_id = :id) AS temContribuicao,
            EXISTS(SELECT 1 FROM inscricao_evento WHERE pessoa_id = :id) AS temInscricao,
            EXISTS(SELECT 1 FROM celula_membro WHERE pessoa_id = :id) AS temCelula,
            EXISTS(SELECT 1 FROM ministerio_membro WHERE pessoa_id = :id) AS temMinisterio
        """, nativeQuery = true)
    VinculosPessoa buscarVinculos(@Param("id") UUID id);

    interface VinculosPessoa {
        boolean getTemUsuario();
        boolean getTemContribuicao();
        boolean getTemInscricao();
        boolean getTemCelula();
        boolean getTemMinisterio();
    }

    @Modifying
    @Query(value = "DELETE FROM pessoa WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}