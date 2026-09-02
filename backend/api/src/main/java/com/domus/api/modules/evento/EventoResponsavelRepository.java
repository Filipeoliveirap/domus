package com.domus.api.modules.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoResponsavelRepository extends JpaRepository<EventoResponsavel, UUID> {

    /** Rede de segurança pra arquivar/excluir uma pessoa: soft delete não dispara FK e o
     *  proxy LAZY estoura. Converte o vínculo dela em texto ("Pessoa removida do sistema"). */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento_responsavel
           SET pessoa_id = NULL, nome_texto = :nome
         WHERE pessoa_id = :pessoaId
        """, nativeQuery = true)
    int desvincularPessoa(@Param("pessoaId") UUID pessoaId, @Param("nome") String nome);
}
