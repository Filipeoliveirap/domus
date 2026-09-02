-- Endereço estruturado AD-HOC do evento: estruturado, mas só daquele evento (não vira
-- LocalEvento reutilizável). Terceira forma de localização, além de local_id e local_texto.
ALTER TABLE evento
    ADD COLUMN cep         VARCHAR(9),
    ADD COLUMN logradouro  VARCHAR(255),
    ADD COLUMN numero      VARCHAR(20),
    ADD COLUMN complemento VARCHAR(255),
    ADD COLUMN bairro      VARCHAR(255),
    ADD COLUMN cidade      VARCHAR(255),
    ADD COLUMN uf          CHAR(2);

-- As três formas são mutuamente exclusivas (validado em EventoService; isto é rede de
-- segurança). Substitui o CHECK de V3 (chk_evento_local_exclusivo), que só cobria
-- local_id x local_texto.
ALTER TABLE evento DROP CONSTRAINT IF EXISTS chk_evento_local_exclusivo;

ALTER TABLE evento ADD CONSTRAINT chk_evento_localizacao_unica CHECK (
    (CASE WHEN local_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN local_texto IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN cep IS NOT NULL OR logradouro IS NOT NULL OR cidade IS NOT NULL THEN 1 ELSE 0 END)
  <= 1
);
