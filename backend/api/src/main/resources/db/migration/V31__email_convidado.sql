-- Convidado sem cadastro (nem pessoa nem acompanhante) ganha e-mail próprio — obrigatório
-- no front quando o evento é pago (usado pra mandar o comprovante de pagamento), opcional
-- em evento gratuito. Nulável aqui porque inscrições antigas não têm esse dado.
ALTER TABLE inscricao_evento ADD COLUMN email_convidado VARCHAR(255);
