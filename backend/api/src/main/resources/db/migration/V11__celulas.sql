CREATE TABLE celula (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    dia_semana VARCHAR(20),
    horario    TIME,
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_celula_igreja_nome
    ON celula (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_celula_igreja ON celula (igreja_id) WHERE deleted_at IS NULL;

CREATE TABLE celula_membro (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id    UUID NOT NULL REFERENCES igreja(id),
    celula_id    UUID NOT NULL REFERENCES celula(id),
    pessoa_id    UUID REFERENCES pessoa(id),
    visitante_id UUID REFERENCES visitante(id),
    papel        VARCHAR(20) NOT NULL DEFAULT 'MEMBRO',
    criado_por_usuario_id     UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_celula_membro_xor CHECK (
        (pessoa_id IS NOT NULL AND visitante_id IS NULL) OR
        (pessoa_id IS NULL AND visitante_id IS NOT NULL)
    ),
    CONSTRAINT chk_celula_membro_lider_e_pessoa CHECK (
        papel <> 'LIDER' OR pessoa_id IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_celula_membro_pessoa ON celula_membro (pessoa_id) WHERE pessoa_id IS NOT NULL;
CREATE UNIQUE INDEX ux_celula_membro_visitante ON celula_membro (visitante_id) WHERE visitante_id IS NOT NULL;
CREATE INDEX ix_celula_membro_celula ON celula_membro (celula_id);

ALTER TABLE visitante ADD COLUMN entrou_em_celula_em TIMESTAMP;
ALTER TABLE visitante ADD COLUMN convertido_pessoa_id UUID REFERENCES pessoa(id);
