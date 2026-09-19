-- V43 — Índices cirúrgicos nas tabelas com mais seq_scan em relação a idx_scan.
--
-- Origem: pg_stat_user_tables no Neon, 19/09/2026. Tabelas com seq_scan alto
-- (relativo ao uso de índice) e filtros prováveis não cobertos.
--
-- Apenas índices onde a coluna filtrada é claramente ausente. Não duplicamos
-- índices existentes. Todos com CONCURRENTLY — migration aplica sem travar a tabela.

-- inscricao_evento: 2.324 seq scans vs 404 idx scans. Toda query multi-tenant
-- começa por igreja_id; só existiam índices em evento_id e pessoa_id.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inscricao_evento_igreja
    ON public.inscricao_evento (igreja_id, created_at DESC);

-- celula_membro: 934 seq scans vs 288 idx scans. Tinha índices em celula_id
-- e pessoa_id (unique parcial), mas não em igreja_id.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_celula_membro_igreja_celula
    ON public.celula_membro (igreja_id, celula_id);

-- notificacao: 372 seq scans vs 1.428 idx scans — quase paridade. Mas as queries
-- de "listar notificações da igreja" não usam índice composto com igreja_id.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notificacao_igreja_created
    ON public.notificacao (igreja_id, created_at DESC);

-- pessoa: 15.150 seq scans vs 23.195 idx scans. Os índices existentes cobrem
-- (igreja_id, nome) e (igreja_id, vinculo), mas queries que filtram por
-- deleted_at IS NULL precisam de recheck linha-a-linha. Adicionar parcial.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pessoa_igreja_ativo_nome
    ON public.pessoa (igreja_id, nome)
    WHERE deleted_at IS NULL;

-- evento: 4.705 seq scans vs 21 idx scans (muito desproporcional). Pode ser
-- queries do calendário que filtram por intervalo de datas sem usar o
-- idx_evento_igreja_inicio — adicionar índice ativo.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_evento_igreja_ativo_inicio
    ON public.evento (igreja_id, inicio_em DESC)
    WHERE deleted_at IS NULL;

-- movimentacao_financeira: 2.935 seq scans vs 1.795 idx scans. Já tem 3 índices
-- compostos, mas queries de relatório que filtram só por intervalo de data
-- (sem igreja_id direto, via join) fazem full scan. Adicionar em data_movimentacao.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_movimentacao_data
    ON public.movimentacao_financeira (data_movimentacao DESC)
    WHERE deleted_at IS NULL;
