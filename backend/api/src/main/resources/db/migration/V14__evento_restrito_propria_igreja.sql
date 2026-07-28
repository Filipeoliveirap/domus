ALTER TABLE evento ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT false;

UPDATE evento e
SET restrito_propria_igreja = true
WHERE EXISTS (
    SELECT 1 FROM igreja i
    WHERE i.id = e.igreja_id
      AND (i.igreja_mae_id IS NOT NULL OR EXISTS (SELECT 1 FROM igreja f WHERE f.igreja_mae_id = i.id))
);
