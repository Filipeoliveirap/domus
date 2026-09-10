package com.domus.api.modules.pagamento.cobranca;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobrancaEventoRepository extends JpaRepository<CobrancaEvento, UUID> {

    Optional<CobrancaEvento> findByTokenLinkPublico(String token);

    List<CobrancaEvento> findByInscricaoId(UUID inscricaoId);

    // Achado em revisão de segurança (2026-08-26): sem lock, duas requisições de
    // POST /cobrancas/{id}/pagar quase simultâneas (duplo clique, retry de rede) podiam
    // ler mpPaymentId == null ANTES de qualquer uma travar — a proteção contra segunda
    // tentativa (Critical 5) só barrava o caso sequencial (segunda chegando depois do
    // commit da primeira), não o concorrente de fato. Trava a linha antes do check,
    // serializando as duas: a segunda só lê depois da primeira commitar mpPaymentId.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CobrancaEvento c WHERE c.id = :id")
    Optional<CobrancaEvento> buscarComLock(@Param("id") UUID id);

    // PENDENTE só reserva vaga a partir do momento que existe uma tentativa de pagamento
    // em andamento (mpPaymentId gravado por CobrancaController.pagar) — clicar "Se
    // inscrever" e ficar navegando/decidindo no checkout, sem enviar nada, não deve
    // segurar a vaga de ninguém (decisão do brainstorm de 2026-08-26).
    @Query("""
        SELECT COUNT(c) FROM CobrancaEvento c
        WHERE c.eventoId = :eventoId
        AND (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
             OR (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE
                 AND c.expiraEm > :agora
                 AND c.mpPaymentId IS NOT NULL))
        """)
    long contarPessoasComVagaReservada(@Param("eventoId") UUID eventoId, @Param("agora") Instant agora);

    List<CobrancaEvento> findByStatusAndExpiraEmBefore(StatusCobranca status, Instant momento);

    /** Todas as cobranças de um evento de uma vez (não uma por inscrição em loop) — usado
     *  por {@code InscricaoService.aplicarMudancaValorPago}/{@code calcularImpactoMudancaValorPago}
     *  pra calcular quanto cada pessoa JÁ pagou no total, incluindo histórico de complementos
     *  de reajustes anteriores (2026-08-27). */
    List<CobrancaEvento> findByEventoId(UUID eventoId);

    /** Quais dessas inscrições já têm PELO MENOS uma cobrança PAGO — usado pra diferenciar,
     *  na lista de inscritos, a tag "Pagamento pendente" (nunca pagou nada) de "Falta
     *  complementar" (já pagou o valor original, só falta a diferença de um reajuste de
     *  preço) — as duas são AGUARDANDO_PAGAMENTO, mas significam coisas bem diferentes pra
     *  quem gerencia (2026-08-27). */
    @Query("""
        SELECT DISTINCT c.inscricaoId FROM CobrancaEvento c
        WHERE c.inscricaoId IN :inscricaoIds
          AND c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
        """)
    List<UUID> findInscricaoIdsComCobrancaPaga(@Param("inscricaoIds") List<UUID> inscricaoIds);

    /** {@code true} quando a inscrição já tem PELO MENOS uma cobrança PAGO — usado pelo
     *  {@code CobrancaEventoExpiracaoJob} pra NÃO cancelar quem já pagou o valor original e
     *  só deve um complemento de reajuste (esse fica pendente na lista de inscritos até o
     *  gestor decidir; ver AUDITORIA-MODULO-EVENTOS-PAGAMENTO [C1]). */
    @Query("""
        SELECT (COUNT(c) > 0) FROM CobrancaEvento c
        WHERE c.inscricaoId = :inscricaoId
          AND c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
        """)
    boolean existePagaParaInscricao(@Param("inscricaoId") UUID inscricaoId);

    /** Quais dessas inscrições têm um COMPLEMENTO em aberto: já pagaram algo (cobrança PAGO)
     *  E ainda têm uma cobrança PENDENTE ou EXPIRADO (a diferença de um reajuste que não foi
     *  quitada). Diferente de {@link #findInscricaoIdsComCobrancaPaga}, isto é o que
     *  realmente vale a tag "Falta complementar" na lista de inscritos — e continua valendo
     *  mesmo com a inscrição CONFIRMADA e o complemento já EXPIRADO ([C1]). */
    @Query("""
        SELECT DISTINCT pago.inscricaoId FROM CobrancaEvento pago
        WHERE pago.inscricaoId IN :inscricaoIds
          AND pago.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
          AND EXISTS (
            SELECT 1 FROM CobrancaEvento dev
            WHERE dev.inscricaoId = pago.inscricaoId
              AND dev.status IN (com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE,
                                 com.domus.api.modules.pagamento.cobranca.StatusCobranca.EXPIRADO)
          )
        """)
    List<UUID> findInscricaoIdsComComplementoDevido(@Param("inscricaoIds") List<UUID> inscricaoIds);

    /** Cobranças com estorno pendente (2026-08-27) — usado pra montar a tag "Estorno
     *  pendente" com botão de retry na lista de inscritos; carrega a cobrança inteira (não
     *  só o inscricaoId) porque o retry precisa do {@code id} dela, não da inscrição. */
    List<CobrancaEvento> findByInscricaoIdInAndEstornoPendenteTrue(@Param("inscricaoIds") List<UUID> inscricaoIds);
}
