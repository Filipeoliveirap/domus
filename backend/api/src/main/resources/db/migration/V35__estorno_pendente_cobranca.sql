-- Estorno em massa (evento virou gratuito, preço baixou, arquivar evento pago, remover
-- inscrito não-elegível, etc.) já tratava falha de estorno como "loga e segue" — a pessoa
-- ficava sem o dinheiro de volta e ninguém via isso na tela. `estorno_pendente` marca essa
-- cobrança pra aparecer como pendência na lista de inscritos, com botão de tentar de novo.
ALTER TABLE cobranca_evento ADD COLUMN estorno_pendente BOOLEAN NOT NULL DEFAULT false;
