-- Prazo de inscrição opcional (ver docs/superpowers/specs/2026-09-07-evento-prazo-inscricao-design.md).
ALTER TABLE evento
    ADD COLUMN inscricoes_ate              TIMESTAMP,
    ADD COLUMN permite_cancelar_apos_prazo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN aviso_prazo_proximo_em      TIMESTAMP,
    ADD COLUMN aviso_prazo_fechado_em      TIMESTAMP;

ALTER TABLE inscricao_evento
    ADD COLUMN aviso_prazo_incompleto_em   TIMESTAMP;
