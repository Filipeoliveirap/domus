CREATE TABLE evento (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    descricao TEXT,
    inicio_em TIMESTAMP NOT NULL,
    fim_em TIMESTAMP,
    local VARCHAR(255),
    foto VARCHAR(500),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_evento PRIMARY KEY (id),
    CONSTRAINT fk_evento_igreja FOREIGN KEY (igreja_id) REFERENCES igreja (id) ON DELETE CASCADE
);
CREATE INDEX idx_evento_igreja_inicio ON evento (igreja_id, inicio_em);
CREATE INDEX idx_evento_deleted_at ON evento (deleted_at);