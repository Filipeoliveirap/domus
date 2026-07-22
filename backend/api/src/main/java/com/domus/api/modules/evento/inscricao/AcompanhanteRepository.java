package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AcompanhanteRepository extends JpaRepository<AcompanhanteInscricao, UUID> {

    /**
     * Todos os convidados já adicionados a inscrições CONFIRMADAS do evento — usada por
     * {@code InscricaoService.validarConvidadoNaoDuplicado} (B1) para bloquear o mesmo
     * convidado duas vezes NO MESMO EVENTO (por telefone, com nome como reserva).
     */
    @Query("""
        SELECT a FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    List<AcompanhanteInscricao> listarPorEvento(@Param("eventoId") UUID eventoId);
}
