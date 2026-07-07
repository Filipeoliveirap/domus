DROP INDEX IF EXISTS uq_categoria_nome_igreja;

CREATE UNIQUE INDEX uq_categoria_nome_igreja
    ON categoria_financeira (igreja_id, LOWER(nome))
    WHERE deleted_at IS NULL;