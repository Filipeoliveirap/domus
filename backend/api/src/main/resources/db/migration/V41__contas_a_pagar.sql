-- V41: Contas a pagar (contas fixas e recorrentes de saída).
-- Ver .superpowers/sdd/2026-09-16-contas-a-pagar/task-1-brief.md
-- created_by: José Filipe

-- Serie: conta_a_pagar referencia a si mesma (auto-vinculo de serie).
-- Recorrencia_frequencia e serie_id sao mutuamente exclusivos
-- (ou repete autonomamente, ou repete atrelada a um modelo da serie).
CREATE TABLE conta_a_pagar (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                   UUID NOT NULL REFERENCES igreja(id),
    categoria_id                UUID NOT NULL REFERENCES categoria_financeira(id),
    fornecedor                  VARCHAR(120) NOT NULL,
    descricao                   VARCHAR(255),
    competencia                 DATE,
    linha_digitavel             VARCHAR(80),
    documento_numero            VARCHAR(40),
    cnpj_fornecedor             VARCHAR(20),
    valor                       NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    vencimento                  DATE NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'EM_ABERTO',
    valor_pago                  NUMERIC(15,2) NOT NULL DEFAULT 0,
    pago_em                     DATE,
    serie_id                    UUID NULL REFERENCES conta_a_pagar(id),
    diverge_da_serie            BOOLEAN NOT NULL DEFAULT false,
    recorrencia_frequencia      VARCHAR(20),
    recorrencia_ate             DATE,
    recorrencia_vezes           INT,
    recorrencia_dia_ancora     INT CHECK (recorrencia_dia_ancora BETWEEN 1 AND 28),
    anexo_id                    UUID NULL,
    observacoes                 TEXT,
    criado_por_usuario_id       UUID NULL REFERENCES usuario(id),
    criado_por_texto            VARCHAR(255),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP
);

-- Query mais frequente: lista de contas da igreja por status e vencimento.
CREATE INDEX idx_conta_a_pagar_igreja_status_venc
    ON public.conta_a_pagar (igreja_id, status, vencimento);

-- Serie: lookup rapido da lista de parcelas pelo modelo.
CREATE INDEX idx_conta_a_pagar_serie
    ON public.conta_a_pagar (serie_id);

-- Fornecedor: busca textual por nome da igreja (case-insensitive via unaccent
-- em qualquer query que precise; o indice simples serve de base).
CREATE INDEX idx_conta_a_pagar_fornecedor
    ON public.conta_a_pagar (igreja_id, fornecedor);

-- CHECKs de dominio.
ALTER TABLE conta_a_pagar
    ADD CONSTRAINT chk_conta_status CHECK (status IN ('EM_ABERTO', 'PAGA'));

ALTER TABLE conta_a_pagar
    ADD CONSTRAINT chk_conta_frequencia
    CHECK (recorrencia_frequencia IS NULL OR
           recorrencia_frequencia IN ('MENSAL', 'TRIMESTRAL', 'SEMESTRAL', 'ANUAL'));

COMMENT ON TABLE conta_a_pagar IS 'Contas a pagar (fixas e recorrentes) de saida.';

-- Pagamentos de uma conta (uma ou mais parcelas pagas, vinculadas a movimentacao).
-- Uma conta pode ter N pagamentos; estorno via flag estornado=true (soft-estorno,
-- mantendo o historico). Estorno real (movimentacao de devolucao) fica no backlog.
CREATE TABLE pagamento_conta (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conta_id                    UUID NOT NULL REFERENCES conta_a_pagar(id),
    movimentacao_id              UUID NOT NULL REFERENCES movimentacao_financeira(id),
    valor_pago                  NUMERIC(15,2) NOT NULL CHECK (valor_pago > 0),
    juros_acrescimos            NUMERIC(15,2) NOT NULL DEFAULT 0,
    desconto                    NUMERIC(15,2) NOT NULL DEFAULT 0,
    pago_em                     DATE NOT NULL,
    forma                       VARCHAR(50),
    anexo_id                    UUID NULL,
    estornado                   BOOLEAN NOT NULL DEFAULT false,
    estornado_em                TIMESTAMP,
    estornado_por_usuario_id    UUID NULL REFERENCES usuario(id),
    criado_por_usuario_id       UUID NULL REFERENCES usuario(id),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMP
);

COMMENT ON TABLE pagamento_conta IS 'Parcelas/pagamentos de uma conta_a_pagar.';

-- Forma de pagamento: permite flexibilidade, mas CHECK evita valores livres demais.
ALTER TABLE pagamento_conta
    ADD CONSTRAINT chk_pagamento_forma
    CHECK (forma IS NULL OR forma IN ('PIX', 'BOLETO', 'CARTAO_CREDITO', 'CARTAO_DEBITO',
                                      'DINHEIRO', 'TRANSFERENCIA', 'OUTRO'));
