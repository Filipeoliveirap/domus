-- Task 6: o contorno de elegibilidade ("inscrever mesmo assim") virou uma marca DURÁVEL na
-- inscrição, não só uma decisão momentânea da chamada de POST.
--
-- Sem isto, o admin inscreve o motorista CONGREGANTE num evento exclusivoMembros com
-- confirmado=true (sucesso, elegibilidade contornada), e qualquer edição futura do evento
-- (mesmo trocar a descrição) cancelaria essa inscrição em silêncio junto com os acompanhantes —
-- porque nada distinguia "gente que ficou irregular pela mudança de regra" de "exceção que o
-- próprio admin escolheu abrir".
--
-- DEFAULT false: toda inscrição existente (todas legítimas sob a regra vigente quando foram
-- criadas) preserva o comportamento antigo — só passa a ser candidata a cancelamento se uma
-- regra futura a desqualificar.
ALTER TABLE inscricao_evento
    ADD COLUMN inscrito_por_excecao BOOLEAN NOT NULL DEFAULT false;
