-- V51: Anexo (arquivo generico) e vinculacao as tabelas de contas.
-- created_by: José Filipe

CREATE TABLE IF NOT EXISTS anexo (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id       UUID NOT NULL REFERENCES igreja(id),
    chave           VARCHAR(255) NOT NULL UNIQUE,
    tipo            VARCHAR(50) NOT NULL,
    bytes           BIGINT NOT NULL CHECK (bytes > 0),
    nome_original   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_anexo_igreja ON public.anexo (igreja_id);

COMMENT ON TABLE anexo IS 'Arquivos genericos (boletos, NFes, contratos) armazenados no R2.';

-- Vinculo anexo em conta_a_pagar (o boleto ou NF da conta).
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS anexo_id UUID NULL REFERENCES anexo(id);

-- Vinculo anexo em pagamento_conta (o comprovante de pagamento).
ALTER TABLE pagamento_conta ADD COLUMN IF NOT EXISTS anexo_id UUID NULL REFERENCES anexo(id);
