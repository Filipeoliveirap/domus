CREATE TABLE IF NOT EXISTS configuracao_plataforma (
    chave VARCHAR(100) PRIMARY KEY,
    valor_criptografado TEXT,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
