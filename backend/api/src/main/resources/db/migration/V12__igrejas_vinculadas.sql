-- Igrejas vinculadas (mãe e congregações).
-- Auto-referência: uma congregação É uma igreja que tem mãe. Nenhum dado muda —
-- as duas colunas são nuláveis e NULL = igreja independente, o estado de todas hoje.
ALTER TABLE igreja
  ADD COLUMN igreja_mae_id  UUID REFERENCES igreja(id),
  ADD COLUMN codigo_vinculo VARCHAR(9) UNIQUE;

-- A consulta quente é "quais são minhas filhas?" (WHERE igreja_mae_id = :eu).
CREATE INDEX idx_igreja_mae_id ON igreja (igreja_mae_id) WHERE igreja_mae_id IS NOT NULL;
