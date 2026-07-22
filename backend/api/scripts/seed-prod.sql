-- =====================================================================
-- seed-prod.sql — seed mínimo de produção
-- =====================================================================
--
-- IDEMPOTENTE: apaga (se existir) a igreja de UUID fixo abaixo, na ordem
-- que respeita as FKs, e reinsere do zero. Seguro rodar mais de uma vez.
--
-- Cria SÓ o necessário para o autor logar e começar a usar:
--   - 1 igreja (sem congregações)
--   - 1 pessoa (o autor)
--   - 1 usuário ADMIN_IGREJA com senha_hash NULL — de propósito.
--
-- senha_hash = NULL é DELIBERADO: nenhuma senha de demo pode existir em
-- produção. O login acontece por:
--   (a) Google OAuth — casado pelo e-mail; o primeiro login já prova a
--       posse do e-mail; ou
--   (b) definir senha pelo fluxo "esqueci minha senha" já existente
--       (POST /auth/forgot-password) — nunca por um hash plantado aqui.
--
-- Sem pessoas fictícias, sem eventos, sem movimentações financeiras
-- fictícias. Só as categorias financeiras básicas, que qualquer igreja
-- quer ter no primeiro dia (opcional — pode comentar o bloco se
-- preferir cadastrar na mão pela tela).

BEGIN;

DO $$
DECLARE
    ig uuid := 'f0000000-0000-0000-0000-000000000001';
BEGIN
    DELETE FROM acompanhante_inscricao
     WHERE inscricao_id IN (SELECT id FROM inscricao_evento WHERE igreja_id = ig);
    DELETE FROM inscricao_evento        WHERE igreja_id = ig;
    DELETE FROM movimentacao_financeira WHERE igreja_id = ig;
    DELETE FROM categoria_financeira    WHERE igreja_id = ig;
    DELETE FROM evento                  WHERE igreja_id = ig;
    DELETE FROM usuario                 WHERE igreja_id = ig;
    DELETE FROM pessoa                   WHERE igreja_id = ig;
    DELETE FROM outbox                  WHERE igreja_id = ig;
    UPDATE igreja SET igreja_mae_id = NULL, atualizado_por_usuario_id = NULL,
                       vinculado_por_usuario_id = NULL
     WHERE id = ig;
    DELETE FROM igreja WHERE id = ig;
END $$;

-- Igreja única, independente (igreja_mae_id NULL).
INSERT INTO igreja (id, nome, email, plano)
VALUES ('f0000000-0000-0000-0000-000000000001', 'Igreja Batista Central',
        'contato@batistacentral.org.br', 'PREMIUM');

-- A pessoa do autor. vinculo = MEMBRO porque ele é batizado; ajuste se
-- não for o caso real.
INSERT INTO pessoa (id, igreja_id, nome, email, vinculo)
VALUES ('f0000000-0000-0000-0000-000000000002', 'f0000000-0000-0000-0000-000000000001',
        'José Filipe Oliveira Pereira', 'josefilipe.dev@gmail.com', 'MEMBRO');

-- Usuário ADMIN_IGREJA com senha_hash NULL — login só por Google ou por
-- "esqueci minha senha".
INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, senha_hash, ativo)
SELECT 'f0000000-0000-0000-0000-000000000003', 'f0000000-0000-0000-0000-000000000001',
       'f0000000-0000-0000-0000-000000000002', r.id, NULL, true
FROM role r WHERE r.nome = 'ADMIN_IGREJA';

-- Categorias financeiras básicas do dia 1 (opcional — remova este bloco
-- se preferir cadastrar pela tela).
INSERT INTO categoria_financeira (igreja_id, nome, tipo)
VALUES
    ('f0000000-0000-0000-0000-000000000001', 'Dízimos', 'ENTRADA'),
    ('f0000000-0000-0000-0000-000000000001', 'Ofertas', 'ENTRADA'),
    ('f0000000-0000-0000-0000-000000000001', 'Doações', 'ENTRADA'),
    ('f0000000-0000-0000-0000-000000000001', 'Aluguel/Manutenção', 'SAIDA'),
    ('f0000000-0000-0000-0000-000000000001', 'Energia e Água', 'SAIDA'),
    ('f0000000-0000-0000-0000-000000000001', 'Missões', 'SAIDA');

COMMIT;
