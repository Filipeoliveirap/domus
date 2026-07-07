CREATE TABLE movimentacao_financeira (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id              UUID NOT NULL REFERENCES igreja (id),
    categoria_id           UUID NOT NULL REFERENCES categoria_financeira (id),
    criado_por_usuario_id  UUID NOT NULL REFERENCES usuario (id),
    atualizado_por_usuario_id   UUID REFERENCES usuario (id),
    membro_id              UUID REFERENCES membro (id),
    tipo                   VARCHAR(20)   NOT NULL,
    valor                  NUMERIC(15,2) NOT NULL,
    data_movimentacao      DATE          NOT NULL,
    descricao              TEXT,
    deleted_at             TIMESTAMP,
    created_at             TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_movimentacao_tipo CHECK (tipo IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT chk_movimentacao_valor CHECK (valor > 0)
);

CREATE INDEX idx_movimentacao_igreja_data ON movimentacao_financeira (igreja_id, data_movimentacao);
CREATE INDEX idx_movimentacao_igreja_categoria ON movimentacao_financeira (igreja_id, categoria_id);
CREATE INDEX idx_movimentacao_igreja_membro ON movimentacao_financeira (igreja_id, membro_id);