CREATE TABLE postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    autor_pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo VARCHAR(30) NOT NULL,
    oficial BOOLEAN NOT NULL DEFAULT FALSE,
    titulo VARCHAR(150),
    conteudo TEXT NOT NULL,
    foto_id UUID REFERENCES foto(id),
    versiculo_ref VARCHAR(100),
    fixado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_postagem_igreja_criado ON postagem(igreja_id, criado_em DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_postagem_oficial ON postagem(igreja_id, oficial) WHERE deleted_at IS NULL;

CREATE TABLE curtida_postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postagem_id UUID NOT NULL REFERENCES postagem(id) ON DELETE CASCADE,
    pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo_reacao VARCHAR(20) NOT NULL DEFAULT 'AMEM',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_curtida_postagem_pessoa UNIQUE (postagem_id, pessoa_id)
);

CREATE INDEX idx_curtida_postagem ON curtida_postagem(postagem_id);

CREATE TABLE comentario_postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postagem_id UUID NOT NULL REFERENCES postagem(id) ON DELETE CASCADE,
    autor_pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    conteudo TEXT NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_comentario_postagem ON comentario_postagem(postagem_id, criado_em ASC) WHERE deleted_at IS NULL;
