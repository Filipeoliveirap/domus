-- V44: Anexo (arquivo generico) e vinculacao as tabelas de contas.
-- created_by: José Filipe

-- Anexo: arquivo generico armazenado no R2 (prefixo anexos/), reusa o mesmo padrao
-- de FOTO (metadados em tabela, bytes no bucket). Permite anexo em qualquer entidade
-- que reference esta tabela (conta_a_pagar, pagamento_conta, e mais tarde membros,
-- eventos etc.). O vinculo e NULL por entidade — cada documento fica numa unica
-- entidade; se a mesma NFe for de mais de uma conta, cadastra-se como anexo separado
-- em cada uma (e o R2 ocupa o espaco duplicado — aceitavel no escopo MVP).
CREATE TABLE anexo (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id       UUID NOT NULL REFERENCES igreja(id),
    chave           VARCHAR(255) NOT NULL UNIQUE,
    tipo            VARCHAR(50) NOT NULL,
    bytes           BIGINT NOT NULL CHECK (bytes > 0),
    nome_original   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_anexo_igreja ON public.anexo (igreja_id);

COMMENT ON TABLE anexo IS 'Arquivos genericos (boletos, NFes, contratos) armazenados no R2.';

-- Vinculo anexo em conta_a_pagar (o boleto ou NF da conta).
ALTER TABLE conta_a_pagar ADD COLUMN anexo_id UUID NULL REFERENCES anexo(id);

-- Vinculo anexo em pagamento_conta (o comprovante de pagamento).
ALTER TABLE pagamento_conta ADD COLUMN anexo_id UUID NULL REFERENCES anexo(id);
