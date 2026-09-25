-- V54: Ajustes de schema na tabela conta_a_pagar para versao 54 no Neon DB
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS beneficiario_pessoa_id UUID REFERENCES pessoa(id);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS beneficiario_texto VARCHAR(120);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS competencia DATE;
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS linha_digitavel VARCHAR(80);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS documento_numero VARCHAR(40);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS cnpj_beneficiario VARCHAR(20);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS diverge_da_serie BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS recorrencia_vezes INT;
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS recorrencia_dia_ancora INT CHECK (recorrencia_dia_ancora BETWEEN 1 AND 28);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS observacoes TEXT;
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS criado_por_usuario_id UUID REFERENCES usuario(id);
ALTER TABLE conta_a_pagar ADD COLUMN IF NOT EXISTS criado_por_texto VARCHAR(255);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'conta_a_pagar' AND column_name = 'fornecedor'
    ) THEN
        UPDATE conta_a_pagar 
        SET beneficiario_texto = fornecedor 
        WHERE beneficiario_texto IS NULL AND fornecedor IS NOT NULL;
    END IF;
END $$;