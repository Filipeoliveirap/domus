-- Meio de pagamento, parcelamento e taxa por evento.
-- Ver docs/superpowers/specs/2026-09-09-meio-pagamento-parcelamento-taxa-evento-design.md

ALTER TABLE evento
    ADD COLUMN pagamento_aceita_cartao BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN pagamento_max_parcelas  SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_max_parcelas CHECK (pagamento_max_parcelas BETWEEN 1 AND 12);

-- Valor efetivamente cobrado do pagador (alvo + taxa, com gross-up). NULL até a 1ª
-- tentativa de pagamento. `valor` continua sendo o alvo (o que a igreja quer receber).
ALTER TABLE cobranca_evento
    ADD COLUMN valor_cobrado NUMERIC(10,2);

-- Taxas negociadas da igreja com o Mercado Pago (plano personalizado). NULL = usa o
-- default da config do back (pagamento.taxa.*).
ALTER TABLE conta_pagamento_igreja
    ADD COLUMN taxa_pix_percent                      NUMERIC(5,2),
    ADD COLUMN taxa_cartao_avista_percent            NUMERIC(5,2),
    ADD COLUMN taxa_cartao_parcela_adicional_percent NUMERIC(5,2);
