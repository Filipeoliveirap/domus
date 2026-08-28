-- Achado ao vivo (2026-08-27): cancelar uma inscrição que já tinha recebido um estorno
-- PARCIAL antes (por causa de um reajuste de preço pra baixo, ver InscricaoService
-- .aplicarMudancaValorPago) tentava estornar o valor CHEIO de novo — o Mercado Pago recusa
-- (não existe mais saldo suficiente pra estornar) e a pessoa nunca conseguia cancelar de
-- vez. `valor_estornado` guarda quanto dessa cobrança já foi devolvido de verdade, pra todo
-- estorno futuro (parcial ou o restante na hora de cancelar) saber exatamente quanto ainda
-- pode devolver.
ALTER TABLE cobranca_evento ADD COLUMN valor_estornado NUMERIC(10,2) NOT NULL DEFAULT 0;
