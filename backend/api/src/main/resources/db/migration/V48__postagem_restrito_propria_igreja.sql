-- V48__postagem_restrito_propria_igreja.sql
-- Adiciona controle de restrição da postagem à própria igreja ou compartilhamento com a família/rede.

ALTER TABLE postagem ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT true;

-- Garante que postagens antigas de igrejas que possuem família permaneçam restritas à própria igreja
UPDATE postagem p
SET restrito_propria_igreja = true
WHERE EXISTS (
    SELECT 1 FROM igreja i
    WHERE i.id = p.igreja_id
      AND (i.igreja_mae_id IS NOT NULL OR EXISTS (SELECT 1 FROM igreja f WHERE f.igreja_mae_id = i.id))
);
