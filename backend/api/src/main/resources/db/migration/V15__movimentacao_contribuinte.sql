CREATE TABLE movimentacao_contribuinte (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  movimentacao_id UUID NOT NULL REFERENCES movimentacao_financeira(id) ON DELETE CASCADE,
  pessoa_id       UUID NOT NULL REFERENCES pessoa(id),
  valor           NUMERIC(15,2) NOT NULL CHECK (valor > 0),
  UNIQUE (movimentacao_id, pessoa_id)
);

INSERT INTO movimentacao_contribuinte (movimentacao_id, pessoa_id, valor)
SELECT id, pessoa_id, valor
FROM movimentacao_financeira
WHERE pessoa_id IS NOT NULL;

ALTER TABLE movimentacao_financeira DROP COLUMN pessoa_id;
