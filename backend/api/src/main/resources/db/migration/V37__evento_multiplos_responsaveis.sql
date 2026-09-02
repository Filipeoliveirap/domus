CREATE TABLE evento_responsavel (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id   UUID NOT NULL REFERENCES igreja(id),
    evento_id   UUID NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    pessoa_id   UUID REFERENCES pessoa(id),
    nome_texto  VARCHAR(255),
    CONSTRAINT chk_evento_responsavel_pessoa_ou_texto
        CHECK (pessoa_id IS NOT NULL OR nome_texto IS NOT NULL)
);

-- Uma pessoa não pode ser responsável do mesmo evento duas vezes.
CREATE UNIQUE INDEX uq_evento_responsavel_evento_pessoa
    ON evento_responsavel (evento_id, pessoa_id)
    WHERE pessoa_id IS NOT NULL;

CREATE INDEX idx_evento_responsavel_evento ON evento_responsavel (evento_id);
CREATE INDEX idx_evento_responsavel_pessoa ON evento_responsavel (pessoa_id);

-- Migra o responsável único (pessoa OU texto) de cada evento para a tabela nova.
INSERT INTO evento_responsavel (igreja_id, evento_id, pessoa_id, nome_texto)
SELECT igreja_id, id, responsavel_pessoa_id, responsavel_texto
FROM evento
WHERE responsavel_pessoa_id IS NOT NULL OR responsavel_texto IS NOT NULL;

ALTER TABLE evento
    DROP COLUMN responsavel_pessoa_id,
    DROP COLUMN responsavel_texto;
