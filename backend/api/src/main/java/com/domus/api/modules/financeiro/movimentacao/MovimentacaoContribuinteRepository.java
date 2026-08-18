package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MovimentacaoContribuinteRepository extends JpaRepository<MovimentacaoContribuinte, UUID> {

    /** Exclusão definitiva de pessoa: mantém o rateio, mostra "Pessoa removida do sistema". */
    @Modifying
    @Query(value = "UPDATE movimentacao_contribuinte SET pessoa_id = NULL WHERE pessoa_id = :pessoaId", nativeQuery = true)
    void desvincularPessoa(@Param("pessoaId") UUID pessoaId);
}
