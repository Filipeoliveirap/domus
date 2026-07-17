CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_entidade   VARCHAR(30)  NOT NULL,
    tipo_evento     VARCHAR(20)  NOT NULL,
    entidade_id     UUID         NOT NULL,
    igreja_id       UUID         NOT NULL,
    processado      BOOLEAN      NOT NULL DEFAULT FALSE,
    tentativas      INT          NOT NULL DEFAULT 0,
    erro            TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    processado_at   TIMESTAMP,

    CONSTRAINT chk_outbox_tipo_entidade CHECK (tipo_entidade IN ('MEMBRO', 'EVENTO', 'USUARIO', 'MOVIMENTACAO', 'CATEGORIA')),
    CONSTRAINT chk_outbox_tipo_evento CHECK (tipo_evento IN ('CRIADO', 'ATUALIZADO', 'REMOVIDO'))
);

CREATE INDEX idx_outbox_pendentes ON outbox (processado, created_at) WHERE processado = FALSE;