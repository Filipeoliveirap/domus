package com.domus.api.modules.usuario;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * Dados de sessão do usuário, como projeção — para o {@code GET /auth/me}.
     *
     * <p>Existe porque o principal do Spring Security é uma entidade DESANEXADA: o
     * {@code SecurityFilter} é um servlet filter e roda ANTES do open-in-view (que é um
     * interceptor de MVC), então o {@code EntityManager} usado por ele já fechou quando o
     * controller executa. Ler {@code usuario.getIgreja().getNome()} de lá lança
     * LazyInitializationException ({@code igreja} é LAZY). Uma projeção monta o DTO direto
     * no banco, numa query, sem depender do estado da entidade.
     */
    @Query("""
    SELECT new com.domus.api.modules.auth.DTO.SessaoDTO(
        u.id, u.pessoa.nome, u.role.nome, u.igreja.id, u.igreja.nome)
    FROM Usuario u
    WHERE u.id = :id
    """)
    Optional<SessaoDTO> findSessaoById(@Param("id") UUID id);
    long countByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);

    @Query("""
    SELECT u FROM Usuario u
    WHERE u.igreja.id = :igrejaId
      AND ( :q IS NULL
            OR LOWER(u.pessoa.nome)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(u.pessoa.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) )
    ORDER BY u.ativo DESC, u.pessoa.nome ASC
    """)
    /**
     * Ativos primeiro, depois por nome — e a ordenação é do BANCO, antes de paginar.
     *
     * <p>Ordenar isto no front resolveria só a página aberta: com três páginas, um desativado
     * da página 1 continuaria acima de um ativo da página 2, e a lista pareceria certa em cada
     * tela e errada no conjunto.
     */
    Page<Usuario> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
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

    /**
     * Nome + foto de quem inscreveu, em lote — resolve {@code inscritoPorUsuarioId} da lista
     * de inscritos numa única query (evita N+1 de buscar usuário por linha).
     *
     * <p>Usuários (ou o membro por trás deles) arquivados desde a inscrição simplesmente NÃO
     * aparecem no resultado — {@code @SQLRestriction} de {@link Usuario} e de
     * {@code Pessoa} filtra soft-deletados. O chamador trata ids ausentes no mapa como
     * "quem inscreveu não está mais disponível", sem quebrar a listagem.
     */
    @Query("""
        SELECT new com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo(
            u.id, u.pessoa.nome, u.pessoa.foto.id)
        FROM Usuario u
        WHERE u.id IN :ids
    """)
    List<RegistranteResumo> buscarRegistrantes(@Param("ids") Collection<UUID> ids);
}
