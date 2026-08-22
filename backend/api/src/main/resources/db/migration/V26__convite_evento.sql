-- Convidado sem cadastro ganha inscrição própria (não acompanhante aninhado) — nome e
-- telefone vivem na própria linha quando pessoa_id é nulo POR ESTE MOTIVO. pessoa_id nulo já
-- tinha um significado diferente (Pessoa excluída via LGPD, ver V18) — a distinção entre os
-- dois casos é: convidado sempre tem nome_convidado preenchido; pessoa excluída, não.
ALTER TABLE inscricao_evento ADD COLUMN nome_convidado VARCHAR(255);
ALTER TABLE inscricao_evento ADD COLUMN telefone_convidado VARCHAR(20);

-- Referência informativa a quem convidou (Pessoa da igreja) — nula só quando a linha é de
-- Pessoa cadastrada (não rastreamos "quem convidou" pra quem já é do sistema) ou LGPD-purgada.
ALTER TABLE inscricao_evento ADD COLUMN convidado_por_pessoa_id UUID REFERENCES pessoa(id);

CREATE INDEX idx_inscricao_convidado_por ON inscricao_evento (convidado_por_pessoa_id);

-- Sem CHECK de banco pra "pessoa_id OU nome_convidado preenchido": a exclusão LGPD
-- (desvincularPessoa) produz linhas com os dois nulos, e um CHECK bloquearia esse UPDATE.
-- A regra é só de aplicação (InscricaoService), nunca do banco.
