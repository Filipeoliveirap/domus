
CREATE TABLE membro (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    igreja_id        UUID           NOT NULL,
    nome             VARCHAR(255)   NOT NULL,
    email            VARCHAR(255),
    telefone         VARCHAR(20),
    data_nascimento  DATE,
    endereco         VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ATIVO',
    estado_civil  VARCHAR(20),
    ministerio       VARCHAR(255),
    foto             VARCHAR(500),
    observacoes      TEXT,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_membro PRIMARY KEY (id),
    CONSTRAINT fk_membro_igreja FOREIGN KEY (igreja_id)
    REFERENCES igreja (id) ON DELETE CASCADE
);

CREATE INDEX idx_membro_igreja_nome   ON membro (igreja_id, nome);
CREATE INDEX idx_membro_igreja_status ON membro (igreja_id, status);
CREATE INDEX idx_membro_deleted_at    ON membro (deleted_at);

CREATE UNIQUE INDEX uq_membro_email ON membro (email) WHERE email IS NOT NULL;