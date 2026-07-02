CREATE TABLE usuario (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    igreja_id        UUID         NOT NULL,
    membro_id        UUID         NOT NULL,
    role_id          UUID         NOT NULL,
    senha_hash       VARCHAR(255) NOT NULL,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login_em  TIMESTAMP,
    delete_at        TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_igreja FOREIGN KEY (igreja_id)
    REFERENCES igreja (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_membro FOREIGN KEY (membro_id)
    REFERENCES membro (id),
    CONSTRAINT fk_usuario_role FOREIGN KEY (role_id)
    REFERENCES role (id),
    CONSTRAINT uq_usuario_membro UNIQUE (membro_id)
);

CREATE INDEX idx_usuario_igreja_id ON usuario (igreja_id);