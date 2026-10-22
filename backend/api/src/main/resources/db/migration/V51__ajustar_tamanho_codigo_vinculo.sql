ALTER TABLE igreja ALTER COLUMN codigo_vinculo TYPE VARCHAR(20);

-- Migração defensiva: caso alguma igreja em produção tenha o plano legado 'PREMIUM'
UPDATE igreja SET plano = 'PRO_PLUS' WHERE plano = 'PREMIUM';
