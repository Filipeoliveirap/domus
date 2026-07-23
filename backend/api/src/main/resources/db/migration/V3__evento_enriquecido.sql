-- Extensão necessária para o índice único de local_evento ignorar acento (ver abaixo).
-- Confirmado que o usuário do banco de dev tem permissão para criá-la.
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() nativo não é marcado IMMUTABLE (o dicionário pode mudar por sessão), então o
-- Postgres recusa usá-lo direto em índice. O wrapper fixa o dicionário e assume a garantia
-- de imutabilidade — seguro aqui porque o dicionário 'unaccent' é estável entre chamadas.
-- search_path fixo (pg_catalog, public) é obrigatório: sendo LANGUAGE sql, o corpo da função
-- é resolvido contra o search_path da SESSÃO que a chama, não contra o de quando foi criada.
-- O pg_dump do nosso backup emite `SET search_path = ''` no início do arquivo restaurado;
-- sem o SET aqui, o pg_restore falha em CREATE INDEX ux_local_evento_igreja_nome com
-- "function unaccent(unknown, text) does not exist" — e o job noturno de restore-test
-- (que valida o backup) reprova todas as noites.
CREATE OR REPLACE FUNCTION imutavel_unaccent(text) RETURNS text AS $$
    SELECT unaccent('unaccent', $1)
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT SET search_path = pg_catalog, public;

CREATE TABLE local_evento (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    capacidade INTEGER,
    cep_logradouro_numero        VARCHAR(255),
    complemento_bairro_cidade_uf VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT chk_local_capacidade CHECK (capacidade IS NULL OR capacidade > 0)
);

-- Nome único por igreja, ignorando acento/caixa e considerando só os não arquivados.
CREATE UNIQUE INDEX ux_local_evento_igreja_nome
    ON local_evento (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_local_evento_igreja ON local_evento (igreja_id) WHERE deleted_at IS NULL;

-- O texto livre que já existia vira local_texto. RENAME preserva os dados.
ALTER TABLE evento RENAME COLUMN local TO local_texto;

ALTER TABLE evento
    ADD COLUMN local_id                  UUID REFERENCES local_evento(id) ON DELETE SET NULL,
    ADD COLUMN tipo                      VARCHAR(80),
    ADD COLUMN responsavel_pessoa_id     UUID REFERENCES pessoa(id) ON DELETE SET NULL,
    ADD COLUMN criado_por_usuario_id     UUID REFERENCES usuario(id),
    ADD COLUMN atualizado_por_usuario_id UUID REFERENCES usuario(id),
    ADD COLUMN recorte_etario            VARCHAR(40),
    ADD COLUMN idade_min                 INTEGER,
    ADD COLUMN idade_max                 INTEGER,
    ADD COLUMN restricao_estado_civil    VARCHAR(20),
    ADD COLUMN restricao_sexo            VARCHAR(10);

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_local_exclusivo
        CHECK (local_id IS NULL OR local_texto IS NULL),
    ADD CONSTRAINT chk_evento_idades
        CHECK (idade_min IS NULL OR idade_max IS NULL OR idade_min <= idade_max),
    ADD CONSTRAINT chk_evento_idade_min CHECK (idade_min IS NULL OR idade_min >= 0),
    ADD CONSTRAINT chk_evento_idade_max CHECK (idade_max IS NULL OR idade_max >= 0),
    ADD CONSTRAINT chk_evento_estado_civil
        CHECK (restricao_estado_civil IS NULL
               OR restricao_estado_civil IN ('SOLTEIRO','CASADO','DIVORCIADO','VIUVO')),
    ADD CONSTRAINT chk_evento_restricao_sexo
        CHECK (restricao_sexo IS NULL OR restricao_sexo IN ('HOMEM','MULHER'));

CREATE INDEX ix_evento_local ON evento (local_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_evento_tipo  ON evento (igreja_id, tipo) WHERE deleted_at IS NULL;

ALTER TABLE pessoa
    ADD COLUMN sexo VARCHAR(10),
    ADD CONSTRAINT chk_pessoa_sexo CHECK (sexo IS NULL OR sexo IN ('HOMEM','MULHER'));
