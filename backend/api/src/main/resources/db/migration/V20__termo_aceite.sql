CREATE TABLE termo_aceite (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo       VARCHAR(30) NOT NULL,
    versao     VARCHAR(20) NOT NULL,
    ip         VARCHAR(45),
    aceito_em  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_termo_aceite_tipo CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_PRIVACIDADE'))
);

CREATE INDEX ix_termo_aceite_usuario ON termo_aceite (usuario_id, tipo);
