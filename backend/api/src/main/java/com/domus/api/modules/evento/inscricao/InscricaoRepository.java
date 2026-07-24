package com.domus.api.modules.evento.inscricao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<InscricaoEvento, UUID> {

    /**
     * Vagas contam PESSOAS, não inscrições: cada inscrição confirmada vale 1 (o membro)
     * mais o número de acompanhantes que ele trouxe. Canceladas não contam.
     */
    @Query("""
        SELECT COALESCE(COUNT(i), 0) + COALESCE(
                   (SELECT COUNT(a) FROM AcompanhanteInscricao a
                     WHERE a.inscricao.evento.id = :eventoId
                       AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA), 0)
        FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long contarPessoasConfirmadas(@Param("eventoId") UUID eventoId);

    Optional<InscricaoEvento> findByEventoIdAndPessoaId(UUID eventoId, UUID pessoaId);

    Optional<InscricaoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.pessoa
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    /**
     * Ids paginados de inscrições confirmadas do evento, com busca opcional por nome do
     * inscrito. Separado de {@link #listarComDetalhesPorIds} porque paginar uma query com
     * {@code JOIN FETCH} de coleção (acompanhantes) faz o Hibernate paginar EM MEMÓRIA — a
     * saída é buscar só os ids paginados aqui e os detalhes completos por {@code IN} depois.
     *
     * <p>{@code CAST(:busca AS string)} é necessário mesmo em JPQL: com {@code busca=null},
     * o PostgreSQL infere o tipo do parâmetro pelo primeiro uso ({@code ? IS NULL}, sem
     * pista nenhuma) e escolhe {@code bytea} — {@code lower(bytea)} não existe. O CAST fixa
     * o tipo antes disso (mesmo padrão já usado nas queries nativas de {@code EventoRepository}).
     */
    @Query(value = """
        SELECT i.id FROM InscricaoEvento i
        JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        ORDER BY i.createdAt ASC
        """,
        countQuery = """
        SELECT COUNT(i) FROM InscricaoEvento i
        JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        """)
    Page<UUID> listarIdsPaginadoPorEvento(@Param("eventoId") UUID eventoId,
                                           @Param("busca") String busca,
                                           Pageable pageable);

    /**
     * Mesmas linhas de {@link #listarPaginadoPorEvento} (mesmos ids), mas com pessoa e
     * acompanhantes já carregados — evita N+1 ao montar {@code InscritoResponse} para a
     * página. A ordem é a mesma ({@code createdAt ASC}), então bate 1:1 com a página de ids.
     */
    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.pessoa
        WHERE i.id IN :ids
        ORDER BY i.createdAt ASC
        """)
    List<InscricaoEvento> listarComDetalhesPorIds(@Param("ids") List<UUID> ids);

    List<InscricaoEvento> findByPessoaIdAndStatus(UUID pessoaId, StatusInscricao status);

    /**
     * Inscrições CONFIRMADAS de uma pessoa em eventos {@code exclusivoMembros} — usada quando
     * o vínculo da pessoa deixa de ser MEMBRO, para cancelar automaticamente onde ela não é
     * mais elegível (ver {@link InscricaoService#cancelarInscricoesEmEventosExclusivos}).
     */
    List<InscricaoEvento> findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
            UUID pessoaId, StatusInscricao status);

    /**
     * Ids, dentre {@code pessoaIds}, que já têm inscrição CONFIRMADA no evento — usada para
     * validar a inscrição em lote ANTES de inserir qualquer linha (tudo-ou-nada), permitindo
     * uma mensagem que nomeia quantos já estavam inscritos.
     */
    @Query("""
        SELECT i.pessoa.id FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId AND i.pessoa.id IN :pessoaIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    List<UUID> listarPessoaIdsJaInscritos(@Param("eventoId") UUID eventoId,
                                          @Param("pessoaIds") List<UUID> pessoaIds);

    /** Quantas PESSOAS cadastradas (sem contar convidados) estão inscritas e confirmadas. */
    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countPessoasInscritas(@Param("eventoId") UUID eventoId);

    /** Quantos CONVIDADOS (acompanhantes) estão sob inscrições confirmadas do evento. */
    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countConvidadosInscritos(@Param("eventoId") UUID eventoId);

    /**
     * Quantas PESSOAS cadastradas de fato compareceram (marca de presença, não inscrição) —
     * só faz sentido em evento com {@code controlaPresenca=true}, checagem que é
     * responsabilidade do chamador ({@code EventoRelatorioService}).
     */
    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long countPessoasCompareceram(@Param("eventoId") UUID eventoId);

    /** Quantos CONVIDADOS de fato compareceram. */
    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND a.compareceu = true
    """)
    long countConvidadosCompareceram(@Param("eventoId") UUID eventoId);

    /**
     * Pessoas CADASTRADAS distintas que compareceram de fato em qualquer um dos eventos
     * informados — usada pelo relatório geral ("participantes únicos"). Convidados não
     * entram: sem cadastro, não há como saber se é "a mesma pessoa" entre dois eventos.
     */
    @Query("""
        SELECT COUNT(DISTINCT i.pessoa.id) FROM InscricaoEvento i
        WHERE i.evento.id IN :eventoIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long contarParticipantesUnicos(@Param("eventoIds") List<UUID> eventoIds);
}
