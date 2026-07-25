-- imutavel_unaccent já existe (criada em V3__evento_enriquecido.sql) — só reaproveitar.

CREATE TABLE ministerio (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_ministerio_igreja_nome
    ON ministerio (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_ministerio_igreja ON ministerio (igreja_id) WHERE deleted_at IS NULL;

CREATE TABLE ministerio_membro (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id     UUID NOT NULL REFERENCES igreja(id),
    ministerio_id UUID NOT NULL REFERENCES ministerio(id),
    pessoa_id     UUID NOT NULL REFERENCES pessoa(id),
    papel         VARCHAR(20) NOT NULL DEFAULT 'MEMBRO',
    status        VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ministerio_membro_papel CHECK (papel IN ('LIDER', 'MEMBRO')),
    CONSTRAINT chk_ministerio_membro_status CHECK (status IN ('PENDENTE', 'ATIVO')),
    CONSTRAINT uq_ministerio_membro_pessoa UNIQUE (ministerio_id, pessoa_id)
);

CREATE INDEX ix_ministerio_membro_pessoa ON ministerio_membro (pessoa_id);
CREATE INDEX ix_ministerio_membro_ministerio ON ministerio_membro (ministerio_id);

-- Descarta o texto livre antigo (decisão explícita do spec — sem migrar dado).
ALTER TABLE pessoa DROP COLUMN ministerio;
