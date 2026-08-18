-- Exclusão definitiva de pessoa: registros que alimentam relatório sobrevivem com
-- "Pessoa removida do sistema" em vez de sumir; célula/ministério só desvincula (lista viva).
ALTER TABLE movimentacao_contribuinte ALTER COLUMN pessoa_id DROP NOT NULL;
ALTER TABLE inscricao_evento ALTER COLUMN pessoa_id DROP NOT NULL;

-- convertido_pessoa_id NULL já significa "nunca converteu" — este flag distingue de
-- "converteu, mas a pessoa foi excluída depois".
ALTER TABLE visitante ADD COLUMN convertido_pessoa_removida BOOLEAN NOT NULL DEFAULT FALSE;

-- Rede de segurança pra excluir um usuário de vez (mesmo padrão já usado em evento):
-- sem os *_texto, apagar o usuário quebraria a FK em qualquer registro que ele criou/atualizou.
ALTER TABLE movimentacao_financeira ALTER COLUMN criado_por_usuario_id DROP NOT NULL;
ALTER TABLE movimentacao_financeira ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE movimentacao_financeira ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE celula ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE celula ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE celula_membro ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE celula_membro ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE ministerio ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE ministerio ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE ministerio_membro ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE ministerio_membro ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE visitante ADD COLUMN criado_por_texto VARCHAR(255);
ALTER TABLE visitante ADD COLUMN atualizado_por_texto VARCHAR(255);

ALTER TABLE igreja ADD COLUMN atualizado_por_texto VARCHAR(255);
ALTER TABLE igreja ADD COLUMN vinculado_por_texto VARCHAR(255);
