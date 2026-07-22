-- V2: fotos de pessoa, evento e igreja.
--
-- O bucket é PRIVADO e a imagem é servida pelo próprio Domus (GET /fotos/{id}), com
-- sessão e igreja validadas. URL pública de R2 seria permanente e sem autenticação —
-- inaceitável para rosto de membro, inclusive criança.

CREATE TABLE foto (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID         NOT NULL REFERENCES igreja(id),
    chave      VARCHAR(255) NOT NULL UNIQUE,
    tipo       VARCHAR(50)  NOT NULL,
    bytes      BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_foto_bytes_positivo CHECK (bytes > 0)
);

CREATE INDEX idx_foto_igreja ON foto (igreja_id);
-- A rotina de órfãs filtra por idade; sem este índice ela varre a tabela inteira.
CREATE INDEX idx_foto_created_at ON foto (created_at);

-- As colunas de foto deixam de ser texto e viram FK.
--
-- ON DELETE RESTRICT é a defesa que importa: a rotina de limpeza decide por AUSÊNCIA de
-- referência, e um erro na consulta dela apagaria a foto de alguém para sempre. Com a FK,
-- o banco recusa — a proteção não depende de a consulta estar certa.
ALTER TABLE pessoa
    DROP COLUMN foto,
    ADD COLUMN foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

ALTER TABLE evento
    DROP COLUMN foto,
    ADD COLUMN foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

ALTER TABLE igreja
    DROP COLUMN logo_url,
    ADD COLUMN logo_foto_id UUID REFERENCES foto(id) ON DELETE RESTRICT;

CREATE INDEX idx_pessoa_foto ON pessoa (foto_id);
CREATE INDEX idx_evento_foto ON evento (foto_id);
CREATE INDEX idx_igreja_logo ON igreja (logo_foto_id);

COMMENT ON TABLE foto IS
    'Metadado da foto. Os bytes vivem no R2 (bucket privado); "chave" é o prefixo lá.';
