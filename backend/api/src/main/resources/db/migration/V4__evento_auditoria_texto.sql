-- Mesmo raciocínio do RENAME `local` -> `local_texto` da V3: responsável, criado_por e
-- atualizado_por são FKs para entidades com soft delete (Pessoa/Usuario usam @SQLDelete +
-- @SQLRestriction), então o ON DELETE SET NULL delas NUNCA dispara de verdade — arquivar a
-- pessoa/usuário deixa a FK "pendurada" numa linha que o Hibernate esconde, e resolver o
-- proxy LAZY ao montar EventoResponse estoura EntityNotFoundException, derrubando a
-- listagem INTEIRA de eventos (ver LocalEventoService.arquivar para o mesmo padrão já
-- corrigido para local_id).
--
-- A saída é a mesma: colunas de TEXTO que guardam o nome no momento em que o vínculo é
-- desfeito. Isso preserva a resposta a "quem cadastrou isto?" mesmo depois de a pessoa/
-- usuário ser arquivado — só perdendo a navegação (não dá mais pra clicar e ir ao cadastro),
-- nunca o nome em si.
ALTER TABLE evento
    ADD COLUMN responsavel_texto     VARCHAR(255),
    ADD COLUMN criado_por_texto      VARCHAR(255),
    ADD COLUMN atualizado_por_texto  VARCHAR(255);
