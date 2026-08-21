package com.domus.api.modules.usuario;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    @Query("SELECT u FROM Usuario u WHERE u.pessoa.email = :email")
    Optional<Usuario> findByEmail(@Param("email") String email);

    Optional<Usuario> findByGoogleSub(String googleSub);
    Optional<Usuario> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /** Projeção evita LazyInitializationException: entidade chega desanexada ao controller (SecurityFilter roda antes do open-in-view). */
    @Query("""
    SELECT new com.domus.api.modules.auth.DTO.SessaoDTO(
        u.id, u.pessoa.nome, u.role.nome, u.igreja.id, u.igreja.nome,
        u.pessoa.foto.id, u.pessoa.cargo, u.igreja.sigla, u.igreja.logoFoto.id)
    FROM Usuario u
    WHERE u.id = :id
    """)
    Optional<SessaoDTO> findSessaoById(@Param("id") UUID id);

    /** Id da foto via FK, sem inicializar o proxy LAZY de {@code Foto}. */
    @Query("SELECT u.pessoa.foto.id FROM Usuario u WHERE u.id = :id")
    UUID findFotoIdById(@Param("id") UUID id);
    long countByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);

    List<Usuario> findByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);

    /** Só o id — usado pra notificar em massa (ex.: novo evento) sem carregar a entidade inteira. */
    @Query("SELECT u.id FROM Usuario u WHERE u.igreja.id = :igrejaId AND u.ativo = true")
    List<UUID> findIdsAtivosPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Ordenação no banco (ativos primeiro) antes de paginar — ordenar no front
     *  só acertaria a página aberta, deixando inconsistência entre páginas. */
    @Query("""
    SELECT new com.domus.api.modules.usuario.DTO.UsuarioResponseDTO(
        u.id, u.pessoa.nome, u.pessoa.email, u.role.nome, u.ativo,
        u.ultimoLoginEm, u.createdAt, u.pessoa.foto.id)
    FROM Usuario u
    WHERE u.igreja.id = :igrejaId
      AND ( :q IS NULL
            OR LOWER(u.pessoa.nome)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(u.pessoa.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) )
    ORDER BY u.ativo DESC, u.pessoa.nome ASC
    """)
    Page<com.domus.api.modules.usuario.DTO.UsuarioResponseDTO> buscarPorIgreja(
            @Param("igrejaId") UUID igrejaId,
            @Param("q") String q,
            Pageable pageable);

    @Query(value = """
        SELECT u.* FROM usuario u
        JOIN pessoa m ON m.id = u.pessoa_id
        WHERE m.email = :email
        """, nativeQuery = true)
    Optional<Usuario> findByEmailIncluindoArquivados(@Param("email") String email);
    Optional<Usuario> findByPessoaId(UUID pessoaId);

    @Query(value = """
    SELECT u.* FROM usuario u
    WHERE u.pessoa_id = :pessoaId
    """, nativeQuery = true)
    Optional<Usuario> findByPessoaIdIncluindoArquivados(@Param("pessoaId") UUID pessoaId);

    /** Nome + foto de quem inscreveu, em lote (evita N+1). Soft-deletados não aparecem
     *  — o chamador trata ids ausentes como "não está mais disponível". */
    @Query("""
        SELECT new com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo(
            u.id, u.pessoa.nome, u.pessoa.foto.id)
        FROM Usuario u
        WHERE u.id IN :ids
    """)
    List<RegistranteResumo> buscarRegistrantes(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(value = "DELETE FROM usuario WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    @Query(value = """
        SELECT * FROM usuario
        WHERE igreja_id = :igrejaId AND delete_at IS NOT NULL
        ORDER BY updated_at DESC
        """, nativeQuery = true)
    List<Usuario> findArquivadosPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivados também — tela de Arquivados. */
    @Query(value = "SELECT * FROM usuario WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<Usuario> findByIdAndIgrejaIdIncluindoArquivados(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "UPDATE usuario SET delete_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    long countByIgrejaId(UUID igrejaId);

    /** E-mails de todos os ADMIN_IGREJA ativos — usado pra avisar todo mundo que pode agir
     *  sobre a exclusão da igreja, não só quem agendou. */
    @Query("""
        SELECT DISTINCT u.pessoa.email FROM Usuario u
        WHERE u.igreja.id = :igrejaId AND u.role.nome = 'ADMIN_IGREJA' AND u.ativo = true
    """)
    List<String> buscarEmailsAdminsAtivos(@Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM usuario WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}
