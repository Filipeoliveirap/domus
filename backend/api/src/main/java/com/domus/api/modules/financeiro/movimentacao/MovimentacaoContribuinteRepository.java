package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MovimentacaoContribuinteRepository extends JpaRepository<MovimentacaoContribuinte, UUID> {
}
