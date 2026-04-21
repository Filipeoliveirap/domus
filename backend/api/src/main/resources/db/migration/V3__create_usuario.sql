CREATE TABLE usuario (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    igreja_id        UUID         NOT NULL,
    nome             VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    senha_hash       VARCHAR(255) NOT NULL,
    ativo            BOOLEAN      NOT NULL DEFAULT TRUE,
    ultimo_login_em  TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_igreja FOREIGN KEY (igreja_id)
        REFERENCES igreja (id) ON DELETE CASCADE,
    CONSTRAINT uq_usuario_igreja_email UNIQUE (igreja_id, email)
);

CREATE INDEX idx_usuario_igreja_id ON usuario (igreja_id);

-- Tabela de junção N:N entre usuario e role
CREATE TABLE usuario_role (
    usuario_id  UUID NOT NULL,
    role_id     UUID NOT NULL,

    CONSTRAINT pk_usuario_role PRIMARY KEY (usuario_id, role_id),
    CONSTRAINT fk_usuario_role_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_role_role FOREIGN KEY (role_id)
        REFERENCES role (id) ON DELETE CASCADE
);