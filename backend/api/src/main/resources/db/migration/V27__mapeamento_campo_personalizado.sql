-- Mapeamento de campo personalizado pra dado estruturado de Pessoa (Spec 2). NULL = campo
-- criado manualmente, nunca pula pergunta mesmo se a Pessoa já tiver o dado.
ALTER TABLE campo_personalizado_evento ADD COLUMN mapeamento VARCHAR(20);
