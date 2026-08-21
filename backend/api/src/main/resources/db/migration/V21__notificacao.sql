CREATE TABLE notificacao (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                UUID NOT NULL REFERENCES igreja(id),
    usuario_destinatario_id  UUID NOT NULL REFERENCES usuario(id),
    tipo                     VARCHAR(60) NOT NULL,
    texto                    VARCHAR(500) NOT NULL,
    link                     VARCHAR(255),
    lida                     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notificacao_destinatario ON notificacao (usuario_destinatario_id, lida, created_at DESC);
