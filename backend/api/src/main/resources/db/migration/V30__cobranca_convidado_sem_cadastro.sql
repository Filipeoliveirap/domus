-- V30: cobrança de evento pago para convidado sem cadastro (Visitante/Pessoa de fora,
-- convite público) — resolvida só por inscricao_id, sem pessoa_id nem acompanhante_id.

ALTER TABLE cobranca_evento DROP CONSTRAINT cobranca_evento_pessoa_xor_acompanhante;
ALTER TABLE cobranca_evento ADD CONSTRAINT cobranca_evento_pessoa_xor_acompanhante CHECK (
    (pessoa_id IS NOT NULL AND acompanhante_id IS NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NOT NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NULL)
);

-- NULL = auto-registro anônimo via /convite/{token} (sem sessão, sem usuário nenhum) —
-- mesmo padrão semântico que inscricao_evento.inscrito_por_usuario_id já usa.
ALTER TABLE cobranca_evento ALTER COLUMN criado_por_usuario_id DROP NOT NULL;
