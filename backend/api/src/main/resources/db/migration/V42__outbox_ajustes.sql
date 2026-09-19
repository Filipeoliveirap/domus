-- V42 — Ajustes no outbox para suportar lag de 30s e alertar sobre eventos presos.
--
-- 1. Índice composto: a query do OutboxProcessador filtra por
--    (processado=false AND tentativas<5 ORDER BY created_at LIMIT 100).
--    O índice antigo era só (processado, created_at) WHERE processado=false,
--    então o filtro "tentativas<5" caía em recheck linha-a-linha.
--    Com (processado, tentativas, created_at), o planner cobre os três predicados.
--
-- 2. Comentário na coluna tentativas: documenta o limite de 5 para o operador
--    que abre a tabela direto no SQL Editor. OutboxLimpezaMortosJob alerta
--    diariamente quando algo ultrapassa esse limite.

DROP INDEX IF EXISTS idx_outbox_pendentes;
CREATE INDEX idx_outbox_pendentes_composto
    ON public.outbox (processado, tentativas, created_at)
    WHERE processado = false;

COMMENT ON COLUMN public.outbox.tentativas IS
    'Quantas vezes o processador falhou. >=5 = morto, requer intervencao (reset manual ou limpeza via DELETE WHERE processado=false AND tentativas>=5). OutboxLimpezaMortosJob alerta todo dia as 03:00.';
