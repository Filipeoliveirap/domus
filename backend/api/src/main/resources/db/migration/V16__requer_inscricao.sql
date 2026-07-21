-- V16: evento.requer_inscricao (revisão de 2026-07-21, depois dos protótipos).
--
-- Sem esta coluna, todo evento aceita inscrição — e o botão "Confirmar presença" apareceria
-- até no culto de domingo, onde confirmar não quer dizer nada. O toggle separa o evento que
-- SE ORGANIZA (mostra o botão e a lista) do evento que só ACONTECE.
ALTER TABLE evento
    ADD COLUMN requer_inscricao BOOLEAN NOT NULL DEFAULT FALSE;
