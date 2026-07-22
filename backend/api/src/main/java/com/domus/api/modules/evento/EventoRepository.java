package com.domus.api.modules.evento;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    Optional<Evento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /**
     * Eventos que apontam para um local cadastrado. Usado ao arquivar o local: como o
     * {@code ON DELETE SET NULL} da FK nunca dispara (local usa soft delete via
     * {@code @SQLDelete}, não DELETE de verdade), é este método que resolve o vínculo antes
     * de arquivar — ver {@code LocalEventoService.arquivar}.
     */
    List<Evento> findByLocalIdAndIgrejaId(UUID localId, UUID igrejaId);

    /**
     * Desvincula TODOS os eventos que apontam para o local — inclusive os ARQUIVADOS.
     *
     * <p>Nativa de propósito: {@code Evento} tem {@code @SQLRestriction("deleted_at IS NULL")},
     * então qualquer busca via JPQL/derived query (inclusive {@link #findByLocalIdAndIgrejaId})
     * simplesmente não enxerga eventos arquivados. Um evento arquivado com {@code local_id}
     * apontando pra um local também arquivado ficaria órfão pra sempre — e ao ser restaurado
     * (Fase 3 do roadmap), {@code EventoResponse.from} resolveria o proxy LAZY de local, o
     * {@code @SQLRestriction} de {@code LocalEvento} filtraria a linha, e estouraria
     * {@code EntityNotFoundException} — derrubando a listagem INTEIRA de eventos com HTTP 500.
     * SQL nativo ignora esses filtros do Hibernate e enxerga a tabela como ela é de verdade.
     *
     * <p>Seta {@code local_texto} e zera {@code local_id} NA MESMA instrução: o
     * {@code CHECK (local_id IS NULL OR local_texto IS NULL)} é avaliado por linha ao FIM da
     * instrução (não a cada coluna), então isso é seguro — não separe em dois UPDATEs, ou o
     * CHECK vai violar no meio do caminho (linha com as duas colunas preenchidas ao mesmo tempo).
     *
     * <p>Não filtra por {@code igreja_id}: o local já foi validado como pertencente à igreja
     * ANTES de chegar aqui (ver {@code LocalEventoService.arquivar}), e um {@code local_id} só
     * existe dentro de uma igreja — filtrar de novo seria redundante.
     *
     * <p>{@code clearAutomatically = true}: como o UPDATE roda direto no banco (sem passar
     * pelo Hibernate), qualquer {@code Evento} já carregado na sessão ficaria com o estado
     * ANTIGO em memória (local/localTexto desatualizados) até a transação acabar. Limpar a
     * persistence context força a próxima leitura a ir buscar a linha de novo no banco.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET local_texto = :nomeLocal, local_id = NULL
         WHERE local_id = :localId
        """, nativeQuery = true)
    int desvincularLocal(@Param("localId") UUID localId, @Param("nomeLocal") String nomeLocal);

    /**
     * Trava a LINHA do evento para serializar a contagem de vagas.
     *
     * <p>Sem isto, sob READ COMMITTED duas inscrições simultâneas na última vaga leem a
     * mesma contagem antiga e AMBAS passam. Mesma classe de erro do vínculo de igrejas (V14).
     *
     * <p>O lock é por evento, então inscrições em eventos diferentes não se bloqueiam.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id AND e.igreja.id = :igrejaId")
    Optional<Evento> buscarComLock(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Próximos eventos (a partir de agora), ordenados. Usar Pageable para limitar. */
    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId AND e.inicioEm >= :agora
        ORDER BY e.inicioEm ASC
    """)
    List<Evento> proximos(@Param("igrejaId") UUID igrejaId, @Param("agora") LocalDateTime agora, Pageable pageable);

    /** Contagem de eventos com início dentro de um intervalo (mês / semana). */
    long countByIgrejaIdAndInicioEmBetween(UUID igrejaId, LocalDateTime de, LocalDateTime ate);

    /**
     * B4: ordena por SITUAÇÃO — EM_ANDAMENTO primeiro, depois hoje (ainda não começou, mas
     * começa hoje), depois futuro, ENCERRADO por último. A situação é DERIVADA (não é coluna,
     * ver {@link SituacaoEvento}/{@link Evento#getSituacao()}), então a ordenação precisa
     * refazer a mesma conta em SQL — um {@code CASE WHEN} comparando contra {@code :agora}.
     *
     * <p><b>Por que {@code :agora} vem de fora (Java), e não {@code NOW()}/{@code LOCALTIMESTAMP}
     * do próprio Postgres:</b> descoberto testando de verdade — o servidor Postgres (Neon) roda
     * em UTC, enquanto {@code inicio_em}/{@code fim_em} são {@code TIMESTAMP} SEM FUSO gravados
     * com a hora LOCAL da aplicação (ver {@link Evento#getSituacao()}, que usa
     * {@code LocalDateTime.now()}). Usar {@code LOCALTIMESTAMP} no SQL comparava a hora local
     * salva contra a hora UTC do servidor — um evento em andamento (ex.: 13h-15h local) virava
     * "encerrado" porque 17h UTC já tinha passado do fim. Passar {@code :agora} calculado em
     * Java garante que a ordenação usa exatamente o mesmo relógio que {@code getSituacao()}.
     *
     * <p><b>Por que no SQL, e não em memória:</b> ordenar a página já carregada só reordenaria
     * DENTRO da página atual — cada página continuaria na ordem antiga (por {@code inicio_em}),
     * e o resultado pareceria certo na primeira página e errado nas seguintes. Fazer o
     * {@code CASE WHEN} fazer parte do {@code ORDER BY} do banco mantém a ordem global correta
     * ANTES de paginar, então qualquer página pega a fatia certa da lista já ordenada.
     *
     * <p>Nativa (não JPQL) porque JPQL não tem {@code date_trunc}/cast de data direto; e
     * {@code countQuery} próprio evita o Spring tentar reaproveitar o {@code ORDER BY} (que não
     * faz sentido numa contagem) na query de contagem da paginação.
     */
    @Query(value = """
        SELECT * FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
        ORDER BY
          CASE
            WHEN CAST(:agora AS timestamp) >= e.inicio_em
                 AND CAST(:agora AS timestamp) <= COALESCE(e.fim_em, date_trunc('day', e.inicio_em) + INTERVAL '23:59:59')
              THEN 0
            WHEN CAST(:agora AS timestamp) < e.inicio_em
                 AND CAST(e.inicio_em AS date) = CAST(CAST(:agora AS timestamp) AS date)
              THEN 1
            WHEN CAST(:agora AS timestamp) < e.inicio_em
              THEN 2
            ELSE 3
          END,
          e.inicio_em ASC
        """,
        countQuery = """
        SELECT COUNT(*) FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
        """,
        nativeQuery = true)
    Page<Evento> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                 @Param("q") String q,
                                 @Param("agora") LocalDateTime agora,
                                 Pageable pageable);
}