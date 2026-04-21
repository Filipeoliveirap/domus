CREATE TABLE role (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    nome        VARCHAR(50)     NOT NULL,
    descricao   VARCHAR(255),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_nome UNIQUE (nome)
);

-- Seed das roles padrão
INSERT INTO role (nome, descricao) VALUES
    ('ADMIN_IGREJA', 'Acesso total a todos os módulos da igreja'),
    ('LIDER',        'Acesso de leitura e escrita em membros e eventos'),
    ('MEMBRO',       'Acesso somente leitura aos módulos permitidos');