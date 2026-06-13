-- remove a constraint antiga (e-mail único por igreja)
ALTER TABLE usuario DROP CONSTRAINT uq_usuario_igreja_email;

-- cria a nova (e-mail único no sistema todo)
ALTER TABLE usuario ADD CONSTRAINT uq_usuario_email UNIQUE (email);