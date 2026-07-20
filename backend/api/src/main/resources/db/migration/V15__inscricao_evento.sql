-- V15: inscrição em evento (Spec A).
-- Vagas contam PESSOAS (inscritos CONFIRMADA + seus acompanhantes), não inscrições.

ALTER TABLE evento
    ADD COLUMN vagas               INTEGER,
    ADD COLUMN preco               NUMERIC(10,2),
    ADD COLUMN exclusivo_membros   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exclusivo_batizados BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_vagas_positivas CHECK (vagas IS NULL OR vagas > 0),
    ADD CONSTRAINT chk_evento_preco_positivo  CHECK (preco IS NULL OR preco > 0);

-- ATIVO nunca significou batizado (criança ATIVA não é batizada; quem se mudou é
-- batizado e está INATIVO). Por isso campo próprio, e não reuso de status.
ALTER TABLE membro
    ADD COLUMN batizado     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_batismo DATE;

CREATE TABLE inscricao_evento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID        NOT NULL REFERENCES igreja(id),
    evento_id               UUID        NOT NULL REFERENCES evento(id),
    membro_id               UUID        NOT NULL REFERENCES membro(id),
    inscrito_por_usuario_id UUID        REFERENCES usuario(id),  -- NULL = auto-inscrição
    status                  VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Cancelar NÃO apaga a linha (preserva quem inscreveu quem). Por isso a
    -- reinscrição REAPROVEITA esta linha em vez de inserir outra.
    CONSTRAINT uk_inscricao_evento_membro UNIQUE (evento_id, membro_id)
);

CREATE INDEX idx_inscricao_evento_id  ON inscricao_evento (evento_id);
CREATE INDEX idx_inscricao_membro_id  ON inscricao_evento (membro_id);

CREATE TABLE acompanhante_inscricao (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inscricao_id UUID         NOT NULL REFERENCES inscricao_evento(id) ON DELETE CASCADE,
    nome         VARCHAR(255) NOT NULL,
    telefone     VARCHAR(20),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_acompanhante_inscricao_id ON acompanhante_inscricao (inscricao_id);

COMMENT ON COLUMN inscricao_evento.inscrito_por_usuario_id IS
    'NULL = a própria pessoa se inscreveu. Preenchido = alguém a inscreveu.';
COMMENT ON TABLE acompanhante_inscricao IS
    'Só para quem NÃO é membro da igreja. Existe para responder, ao ler a lista, '
    '"de onde veio essa pessoa que ninguém conhece".';
