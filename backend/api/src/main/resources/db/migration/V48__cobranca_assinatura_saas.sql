ALTER TABLE igreja ADD COLUMN IF NOT EXISTS status_assinatura VARCHAR(30) DEFAULT 'TRIAL';
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS trial_expira_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS mp_preapproval_id VARCHAR(100);
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS mp_payer_id VARCHAR(100);

UPDATE igreja SET plano = 'BASICO' WHERE plano IS NULL;
UPDATE igreja SET status_assinatura = 'ATIVA' WHERE status_assinatura IS NULL;

CREATE TABLE IF NOT EXISTS codigo_convite_congregacao (
    id BIGSERIAL PRIMARY KEY,
    matriz_id BIGINT NOT NULL REFERENCES igreja(id),
    codigo VARCHAR(20) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    usado_em TIMESTAMP WITH TIME ZONE,
    igreja_filha_id BIGINT REFERENCES igreja(id)
);
