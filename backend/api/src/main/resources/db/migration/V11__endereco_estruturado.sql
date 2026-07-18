-- Troca o endereco de texto livre por colunas estruturadas.
-- Destrutivo de propósito: prod está vazio; o dado de dev é descartável (não se migra texto).
ALTER TABLE membro
  DROP COLUMN endereco,
  ADD COLUMN cep         VARCHAR(9),
  ADD COLUMN logradouro  VARCHAR(255),
  ADD COLUMN numero      VARCHAR(20),
  ADD COLUMN complemento VARCHAR(255),
  ADD COLUMN bairro      VARCHAR(255),
  ADD COLUMN cidade      VARCHAR(255),
  ADD COLUMN uf          CHAR(2);
