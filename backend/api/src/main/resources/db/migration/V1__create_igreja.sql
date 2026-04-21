CREATE TABLE igreja (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    nome VARCHAR(255) NOT NULL,
    cnpj VARCHAR(18),
    email VARCHAR(255) NOT NULL,
    telefone VARCHAR(20),
    plano VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_igreja PRIMARY KEY (id)
);