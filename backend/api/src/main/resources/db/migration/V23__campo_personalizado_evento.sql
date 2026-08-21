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
