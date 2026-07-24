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
     * Desvincula o RESPONSÁVEL de todos os eventos que apontam para essa pessoa — inclusive os
     * ARQUIVADOS. Mesmo padrão de {@link #desvincularLocal}: {@code responsavel_pessoa_id}
     * também tem {@code ON DELETE SET NULL} que nunca dispara ({@link Pessoa} usa soft delete),
     * e {@code Evento} tem {@code @SQLRestriction}, então só SQL nativo enxerga arquivados.
     * Chamado por {@code PessoaService.arquivarMembro} ANTES do soft delete da pessoa.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET responsavel_texto = :nome, responsavel_pessoa_id = NULL
         WHERE responsavel_pessoa_id = :pessoaId
        """, nativeQuery = true)
    int desvincularResponsavel(@Param("pessoaId") UUID pessoaId, @Param("nome") String nome);

    /**
     * Desvincula um USUÁRIO de {@code criado_por_usuario_id} e/ou {@code atualizado_por_usuario_id}
     * — o mesmo usuário pode aparecer nas duas colunas do mesmo evento (criou e depois editou),
     * por isso o {@code CASE WHEN} trata as duas independentemente numa única instrução.
     *
     * <p>Diferente do responsável, aqui não existe "opcional": TODO evento tem
     * {@code criado_por_usuario_id} preenchido — arquivar um usuário que já cadastrou qualquer
     * evento do sistema, sem este desvínculo, derrubaria a listagem inteira (o pior dos dois
     * casos encontrados na revisão). Chamado tanto por {@code UsuarioService.arquivarUsuario}
     * quanto por {@code arquivarPorMembro} (cascata do arquivamento de pessoa).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

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
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
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
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
        """,
        nativeQuery = true)
    Page<Evento> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                 @Param("q") String q,
                                 @Param("tipo") String tipo,
                                 @Param("recorteEtario") String recorteEtario,
                                 @Param("agora") LocalDateTime agora,
                                 Pageable pageable);

    /**
     * Tipos já usados pela igreja, do mais frequente para o menos frequente (empate por ordem
     * alfabética). Alimenta {@code GET /eventos/tipos}: é isso que faz o campo "aprender" —
     * o que a igreja mais digita sobe e passa na frente das sementes que ninguém usou.
     */
    @Query("""
        SELECT e.tipo FROM Evento e
         WHERE e.igreja.id = :igrejaId AND e.tipo IS NOT NULL
         GROUP BY e.tipo
         ORDER BY COUNT(e) DESC, e.tipo ASC
        """)
    List<String> tiposUsadosPorFrequencia(@Param("igrejaId") UUID igrejaId);

    /**
     * Eventos filtrados para o relatório geral — mais recente primeiro. Todos os filtros são
     * combináveis e opcionais (spec: Período, Recorte Etário, Tipo).
     *
     * <p>Nativa (não JPQL) porque JPQL com parâmetro nulável que o PostgreSQL vê primeiro como
     * {@code IS NULL} não consegue inferir o tipo da coluna — dá "could not determine data type
     * of parameter". {@code CAST} explícito resolve, mesmo padrão de {@link #buscarPorIgreja}.
     */
    @Query(value = """
        SELECT * FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND (CAST(:inicio AS timestamp) IS NULL OR e.inicio_em >= CAST(:inicio AS timestamp))
          AND (CAST(:fim AS timestamp) IS NULL OR e.inicio_em <= CAST(:fim AS timestamp))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
        ORDER BY e.inicio_em DESC
        """, nativeQuery = true)
    List<Evento> buscarParaRelatorio(@Param("igrejaId") UUID igrejaId,
                                      @Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim,
                                      @Param("recorteEtario") String recorteEtario,
                                      @Param("tipo") String tipo);

    /**
     * Eventos que CONTROLAM presença, a partir de {@code desde} — alimenta o gráfico de
     * tendência (Decisão 4: só quem ativou controle de presença entra na conta; mês sem
     * nenhum evento assim vira {@code null}, nunca zero). Respeita recorte etário/tipo, mas
     * NÃO o filtro de período do relatório geral — a tendência tem sua própria janela fixa
     * de 6 meses.
     *
     * <p>Nativa pela mesma razão de {@link #buscarParaRelatorio}: parâmetros nuláveis com
     * {@code IS NULL} precisam de {@code CAST} explícito para o PostgreSQL inferir o tipo.
     */
    @Query(value = """
        SELECT * FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND e.controla_presenca = true
          AND e.inicio_em >= :desde
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
        ORDER BY e.inicio_em ASC
        """, nativeQuery = true)
    List<Evento> buscarComControlaPresenca(@Param("igrejaId") UUID igrejaId,
                                            @Param("desde") LocalDateTime desde,
                                            @Param("recorteEtario") String recorteEtario,
                                            @Param("tipo") String tipo);

    /**
     * "Evento anterior do mesmo tipo" (Decisão 4 do spec): o mais recente da mesma igreja,
     * mesmo {@code tipo}, com {@code inicioEm} anterior ao evento atual.
     */
    Optional<Evento> findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
            UUID igrejaId, String tipo, LocalDateTime inicioEm);
}