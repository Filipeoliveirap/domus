CREATE TABLE visitante (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id       UUID NOT NULL REFERENCES igreja(id),
    nome            VARCHAR(255) NOT NULL,
    telefone        VARCHAR(20),
    cep             VARCHAR(9),
    logradouro      VARCHAR(255),
    numero          VARCHAR(20),
    complemento     VARCHAR(255),
    bairro          VARCHAR(255),
    cidade          VARCHAR(255),
    uf              CHAR(2),
    sexo            VARCHAR(20),
    estado_civil    VARCHAR(20),
    data_nascimento DATE,
    tem_filhos       BOOLEAN NOT NULL DEFAULT false,
    quantidade_filhos INTEGER,
    observacoes     TEXT,
    contato_realizado       BOOLEAN NOT NULL DEFAULT false,
    visita_realizada        BOOLEAN NOT NULL DEFAULT false,
    acompanhamento_feito    BOOLEAN NOT NULL DEFAULT false,
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_visitante_quantidade_filhos
        CHECK (quantidade_filhos IS NULL OR quantidade_filhos >= 0)
);

CREATE INDEX ix_visitante_igreja ON visitante (igreja_id);
