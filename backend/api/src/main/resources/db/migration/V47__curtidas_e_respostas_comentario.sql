-- V47__curtidas_e_respostas_comentario.sql
ALTER TABLE comentario_postagem
    ADD COLUMN pai_comentario_id UUID REFERENCES comentario_postagem(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS curtida_comentario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comentario_id UUID NOT NULL REFERENCES comentario_postagem(id) ON DELETE CASCADE,
    pessoa_id UUID NOT NULL REFERENCES pessoa(id) ON DELETE CASCADE,
    criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_curtida_comentario_pessoa UNIQUE (comentario_id, pessoa_id)
);

CREATE INDEX idx_comentario_pai ON comentario_postagem(pai_comentario_id);
CREATE INDEX idx_curtida_comentario ON curtida_comentario(comentario_id);
