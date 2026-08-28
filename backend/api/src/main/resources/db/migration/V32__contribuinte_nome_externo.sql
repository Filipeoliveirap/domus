-- Permite registrar contribuinte/beneficiário de fora (sem cadastro na igreja) — ex.: doação
-- de visitante avulso. pessoa_id continua sendo o caminho normal pra quem tem cadastro;
-- nome_externo é o texto livre pro caso sem cadastro.
--
-- Não exige exatamente um dos dois (só proíbe os dois preenchidos ao mesmo tempo): quando
-- uma pessoa é excluída definitivamente (LGPD), PessoaExclusaoDefinitivaService zera
-- pessoa_id do contribuinte de propósito, sem preencher nome_externo — vira "Pessoa
-- removida do sistema" na exibição (ver MovimentacaoResponse.de). Os dois nulos ao mesmo
-- tempo é esse estado legítimo, não um erro de cadastro.
ALTER TABLE movimentacao_contribuinte ADD COLUMN nome_externo VARCHAR(255);

ALTER TABLE movimentacao_contribuinte ADD CONSTRAINT movimentacao_contribuinte_identidade_check
    CHECK (NOT (pessoa_id IS NOT NULL AND nome_externo IS NOT NULL));
