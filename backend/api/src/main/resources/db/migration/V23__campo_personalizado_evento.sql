CREATE TABLE campo_personalizado_evento (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id           UUID NOT NULL REFERENCES igreja(id),
    evento_id           UUID NOT NULL REFERENCES evento(id),
    label               VARCHAR(120) NOT NULL,
    placeholder         VARCHAR(160),
    tipo                VARCHAR(20) NOT NULL,
    opcoes              TEXT,
    obrigatorio         BOOLEAN NOT NULL DEFAULT FALSE,
    visivel_ao_publico  BOOLEAN NOT NULL DEFAULT TRUE,
    ordem               INTEGER NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_campo_personalizado_evento ON campo_personalizado_evento (evento_id);

CREATE TABLE resposta_campo_personalizado (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campo_id        UUID NOT NULL REFERENCES campo_personalizado_evento(id),
    inscricao_id    UUID NOT NULL REFERENCES inscricao_evento(id),
    acompanhante_id UUID REFERENCES acompanhante_inscricao(id),
    valor           TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX idx_resposta_campo_inscricao ON resposta_campo_personalizado (inscricao_id);

-- UNIQUE simples não serve: no Postgres, NULL nunca é igual a NULL numa constraint UNIQUE,
-- então duas respostas do titular (acompanhante_id sempre NULL) não seriam bloqueadas.
CREATE UNIQUE INDEX idx_resposta_titular_unica
    ON resposta_campo_personalizado (campo_id, inscricao_id)
    WHERE acompanhante_id IS NULL;

CREATE UNIQUE INDEX idx_resposta_acompanhante_unica
    ON resposta_campo_personalizado (campo_id, acompanhante_id)
    WHERE acompanhante_id IS NOT NULL;
