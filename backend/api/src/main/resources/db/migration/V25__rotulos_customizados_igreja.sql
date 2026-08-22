ALTER TABLE igreja
  ADD COLUMN ministerio_nome_singular   VARCHAR(40),
  ADD COLUMN ministerio_nome_plural     VARCHAR(40),
  ADD COLUMN ministerio_genero          VARCHAR(9) CHECK (ministerio_genero IN ('MASCULINO', 'FEMININO')),
  ADD COLUMN congregacao_nome_singular  VARCHAR(40),
  ADD COLUMN congregacao_nome_plural    VARCHAR(40),
  ADD COLUMN congregacao_genero         VARCHAR(9) CHECK (congregacao_genero IN ('MASCULINO', 'FEMININO')),
  ADD COLUMN celula_nome_singular       VARCHAR(40),
  ADD COLUMN celula_nome_plural         VARCHAR(40),
  ADD COLUMN celula_genero              VARCHAR(9) CHECK (celula_genero IN ('MASCULINO', 'FEMININO'));
