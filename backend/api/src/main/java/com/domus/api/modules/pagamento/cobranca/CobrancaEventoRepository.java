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
}
