package com.domus.api.modules.igreja.exclusao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Purga tabela-por-tabela da igreja (Fase 2 preenche o corpo de {@link #purgar}). */
@Slf4j
@Service
public class PurgaIgrejaService {

    @Transactional
    public void purgar(UUID igrejaId) {
        log.warn("purgar() chamado antes da Fase 2 estar implementada. igreja_id={}", igrejaId);
    }
}
