-- V29: cobrança de evento pago via Mercado Pago (split).
-- Duas entidades novas: CONTA_PAGAMENTO_IGREJA (credencial OAuth da igreja no Mercado
-- Pago, 1-1) e COBRANCA_EVENTO (uma linha por pessoa cobrada num evento pago).

CREATE TABLE conta_pagamento_igreja (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID NOT NULL UNIQUE REFERENCES igreja(id),
    mp_user_id              VARCHAR(100) NOT NULL,
    -- access_token/refresh_token são criptografados em repouso (AES-GCM, ver
    -- CredencialEncryptor) — nunca gravados em texto puro, nunca logados.
    access_token            TEXT NOT NULL,
    refresh_token           TEXT NOT NULL,
    expira_em               TIMESTAMPTZ NOT NULL,
    conectado_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    conectado_por_usuario_id UUID NOT NULL REFERENCES usuario(id)
);

CREATE TABLE cobranca_evento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID NOT NULL REFERENCES igreja(id),
    evento_id               UUID NOT NULL REFERENCES evento(id),
    inscricao_id            UUID NOT NULL REFERENCES inscricao_evento(id) ON DELETE CASCADE,
    pessoa_id               UUID REFERENCES pessoa(id),
    acompanhante_id         UUID REFERENCES acompanhante_inscricao(id) ON DELETE CASCADE,
    valor                   NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    mp_payment_id           VARCHAR(100),
    token_link_publico      VARCHAR(64) UNIQUE,
    expira_em               TIMESTAMPTZ NOT NULL,
    pago_em                 TIMESTAMPTZ,
    criado_por_usuario_id   UUID NOT NULL REFERENCES usuario(id),
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Exatamente uma das duas: cobrança é de uma pessoa cadastrada OU de um
    -- acompanhante sem cadastro (mesmo padrão de EVENTO.local_id/local_texto).
    CONSTRAINT cobranca_evento_pessoa_xor_acompanhante CHECK (
        (pessoa_id IS NOT NULL AND acompanhante_id IS NULL) OR
        (pessoa_id IS NULL AND acompanhante_id IS NOT NULL)
    ),
    CONSTRAINT cobranca_evento_status_valido CHECK (
        status IN ('PENDENTE', 'PAGO', 'EXPIRADO', 'CANCELADO', 'REEMBOLSADO')
    )
);

CREATE INDEX idx_cobranca_evento_inscricao ON cobranca_evento(inscricao_id);
CREATE INDEX idx_cobranca_evento_status_expiracao ON cobranca_evento(status, expira_em)
    WHERE status = 'PENDENTE';
