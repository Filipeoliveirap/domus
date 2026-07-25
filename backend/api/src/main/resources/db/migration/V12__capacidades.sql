CREATE TABLE usuario_capacidade (
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    capacidade  VARCHAR(20) NOT NULL,
    concedido_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_id, capacidade),
    CONSTRAINT chk_usuario_capacidade_valor CHECK (capacidade IN ('SECRETARIO', 'TESOUREIRO'))
);

CREATE INDEX ix_usuario_capacidade_usuario ON usuario_capacidade (usuario_id);
