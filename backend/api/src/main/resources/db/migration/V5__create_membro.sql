CREATE TYPE membro_status AS ENUM ('ATIVO', 'INATIVO', 'VISITANTE');

CREATE TABLE membro (
    id               UUID           NOT NULL DEFAULT gen_random_uuid(),
    igreja_id        UUID           NOT NULL,
    nome             VARCHAR(255)   NOT NULL,
    email            VARCHAR(255),
    telefone         VARCHAR(20),
    data_nascimento  DATE,
    endereco         VARCHAR(500),
    status           membro_status  NOT NULL DEFAULT 'ATIVO',
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