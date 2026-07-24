-- Task 1: presença é opt-in por evento — "dar baixa" em quem realmente compareceu,
-- separado de quem apenas se inscreveu. Granular por PESSOA FÍSICA (inscrito e cada
-- acompanhante), porque acompanhante ocupa vaga e esteve lá igual.
--
-- CHECK: só pode controlar presença quem já organiza lista de inscrição (requer_inscricao).
-- Sem lista prévia não há quem "chamar" — controlar presença sem inscrição não faz sentido.
ALTER TABLE evento
    ADD COLUMN controla_presenca BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_controla_presenca_exige_inscricao
        CHECK (NOT controla_presenca OR requer_inscricao);

ALTER TABLE inscricao_evento
    ADD COLUMN compareceu BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE acompanhante_inscricao
    ADD COLUMN compareceu BOOLEAN NOT NULL DEFAULT false;
