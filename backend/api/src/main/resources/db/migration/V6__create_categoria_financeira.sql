CREATE TABLE categoria_financeira (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id     UUID NOT NULL REFERENCES igreja (id),
    nome          VARCHAR(255) NOT NULL,
    tipo          VARCHAR(20)  NOT NULL,
    deleted_at    TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_categoria_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA', 'AMBOS'))
);

CREATE UNIQUE INDEX uq_categoria_nome_igreja
    ON categoria_financeira (igreja_id, nome)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_categoria_igreja ON categoria_financeira (igreja_id);