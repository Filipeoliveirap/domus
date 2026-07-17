-- Suporte a contas que entram só pelo Google:
-- senha_hash passa a aceitar NULL (conta sem senha nativa)
-- google_sub guarda o ID imutável do Google, único (múltiplos NULLs permitidos pelo Postgres)
ALTER TABLE usuario ALTER COLUMN senha_hash DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN google_sub VARCHAR(255);
CREATE UNIQUE INDEX ux_usuario_google_sub ON usuario (google_sub);
