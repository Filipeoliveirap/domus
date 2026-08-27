-- V33: unifica "acompanhante" (modelo antigo, aninhado, sem e-mail) com "convidado sem
-- cadastro" (nome_convidado/telefone_convidado/email_convidado direto em
-- inscricao_evento) — cada acompanhante vira sua própria inscrição, independente,
-- ligada por convidado_por_pessoa_id (já existe desde V26) ao titular que trouxe.
--
-- Truque: a nova inscricao_evento nasce com o MESMO id da acompanhante_inscricao
-- original — assim cobranca_evento.acompanhante_id e
-- resposta_campo_personalizado.acompanhante_id, que já apontam pra esse id, só
-- precisam trocar de coluna (repontar pra inscricao_id), sem tabela de mapeamento.

INSERT INTO inscricao_evento (
    id, igreja_id, evento_id, pessoa_id, status,
    nome_convidado, telefone_convidado, email_convidado,
    convidado_por_pessoa_id, compareceu, created_at
)
SELECT
    a.id, i.igreja_id, i.evento_id, NULL, i.status,
    a.nome, a.telefone, NULL,
    i.pessoa_id, a.compareceu, a.created_at
FROM acompanhante_inscricao a
JOIN inscricao_evento i ON i.id = a.inscricao_id;

-- Repontar respostas de campo personalizado que hoje ligam por acompanhante_id.
UPDATE resposta_campo_personalizado
SET inscricao_id = acompanhante_id
WHERE acompanhante_id IS NOT NULL;

-- Repontar cobranças que hoje ligam por acompanhante_id — vira o mesmo formato de
-- "convidado sem cadastro" (pessoa_id NULL, inscricao_id aponta pra própria inscrição).
UPDATE cobranca_evento
SET inscricao_id = acompanhante_id, acompanhante_id = NULL
WHERE acompanhante_id IS NOT NULL;

ALTER TABLE resposta_campo_personalizado DROP COLUMN acompanhante_id;
ALTER TABLE cobranca_evento DROP CONSTRAINT IF EXISTS cobranca_evento_pessoa_xor_acompanhante;
ALTER TABLE cobranca_evento DROP COLUMN acompanhante_id;
DROP TABLE acompanhante_inscricao;
