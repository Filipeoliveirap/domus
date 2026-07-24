    -- ============================================================================
--  Seed de demonstração — escopo do TCC (branch develop)
--
--  Pré-requisito: a aplicação já subiu UMA vez contra este banco, para o
--  Flyway criar o schema (V1..V9). Este script só popula dados.
--
--  Rodar com:  ./scripts/seed-demo.sh
--
--  Datas são relativas a CURRENT_DATE, então a demo nunca fica desatualizada.
--  Ao final, insere os eventos de outbox para o OutboxProcessador indexar
--  tudo no Elasticsearch (roda a cada 3s enquanto a API estiver de pé).
-- ============================================================================

BEGIN;

-- Limpa dados de demo anteriores (role vem da migration V2, não se mexe).
TRUNCATE TABLE outbox, movimentacao_financeira, categoria_financeira,
               evento, usuario, membro, igreja RESTART IDENTITY CASCADE;

-- ─── Igreja ─────────────────────────────────────────────────────────────────
INSERT INTO igreja (id, nome, cnpj, email, telefone, plano) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'Igreja Batista Central',
     '12.345.678/0001-90',
     'contato@ibcentral.org.br',
     '(88) 3611-1000',
     'PRO');

-- ─── Membros ────────────────────────────────────────────────────────────────
-- Os três primeiros têm usuário de acesso (admin, líder, membro).
INSERT INTO membro (id, igreja_id, nome, email, telefone, data_nascimento,
                    endereco, status, estado_civil, ministerio, observacoes)
VALUES
 ('22222222-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','Filipe Oliveira','admin@domus.dev','(88) 99900-0001','1998-03-12','Rua das Acácias, 120 - Centro','ATIVO','SOLTEIRO','Pastoral','Administrador do sistema'),
 ('22222222-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','Marina Albuquerque','lider@domus.dev','(88) 99900-0002','1985-07-25','Av. Dom Pedro, 45 - Aldeota','ATIVO','CASADO','Louvor','Líder do ministério de louvor'),
 ('22222222-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','Renato Freitas','membro@domus.dev','(88) 99900-0003','1992-11-02','Rua São Judas, 310 - Benfica','ATIVO','CASADO','Diaconia',NULL),
 ('22222222-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','Ana Beatriz Colares','ana.colares@exemplo.com','(88) 99900-0004','2001-01-19','Rua Padre Cícero, 88 - Montese','ATIVO','SOLTEIRO','Infantil',NULL),
 ('22222222-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','Josué Tavares','josue.tavares@exemplo.com','(88) 99900-0005','1977-05-30','Av. Beira Rio, 500 - Centro','ATIVO','CASADO','Ensino',NULL),
 ('22222222-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','Cláudia Menezes','claudia.menezes@exemplo.com','(88) 99900-0006','1969-09-08','Rua das Flores, 17 - Parquelândia','ATIVO','VIUVO','Intercessão',NULL),
 ('22222222-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','Paulo Henrique Lima','paulo.lima@exemplo.com','(88) 99900-0007','1995-12-14','Rua Coronel Souza, 902 - Centro','ATIVO','SOLTEIRO','Mídia',NULL),
 ('22222222-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','Larissa Andrade','larissa.andrade@exemplo.com','(88) 99900-0008','1990-04-03','Rua Sete de Setembro, 233 - Jacarecanga','ATIVO','CASADO','Louvor',NULL),
 ('22222222-0000-0000-0000-000000000009','11111111-1111-1111-1111-111111111111','Marcos Vinícius Rocha','marcos.rocha@exemplo.com','(88) 99900-0009','1983-08-21','Av. Alberto Craveiro, 1200 - Castelão','ATIVO','DIVORCIADO','Diaconia',NULL),
 ('22222222-0000-0000-0000-000000000010','11111111-1111-1111-1111-111111111111','Sônia Regina Barros','sonia.barros@exemplo.com','(88) 99900-0010','1961-02-27','Rua do Rosário, 64 - Centro','ATIVO','CASADO','Intercessão','Membro fundadora'),
 ('22222222-0000-0000-0000-000000000011','11111111-1111-1111-1111-111111111111','Diego Nascimento','diego.nascimento@exemplo.com','(88) 99900-0011','1999-06-11','Rua Pedro Pereira, 410 - Centro','ATIVO','SOLTEIRO','Jovens',NULL),
 ('22222222-0000-0000-0000-000000000012','11111111-1111-1111-1111-111111111111','Patrícia Gomes','patricia.gomes@exemplo.com','(88) 99900-0012','1988-10-05','Av. Santos Dumont, 3300 - Aldeota','ATIVO','CASADO','Infantil',NULL),
 ('22222222-0000-0000-0000-000000000013','11111111-1111-1111-1111-111111111111','Eduardo Sampaio','eduardo.sampaio@exemplo.com','(88) 99900-0013','1975-03-17','Rua Meton de Alencar, 75 - Centro','ATIVO','CASADO','Ensino',NULL),
 ('22222222-0000-0000-0000-000000000014','11111111-1111-1111-1111-111111111111','Juliana Peixoto','juliana.peixoto@exemplo.com','(88) 99900-0014','1997-07-09','Rua Barão do Rio Branco, 1500 - Centro','ATIVO','SOLTEIRO','Mídia',NULL),
 ('22222222-0000-0000-0000-000000000015','11111111-1111-1111-1111-111111111111','Roberto Carvalho','roberto.carvalho@exemplo.com','(88) 99900-0015','1966-11-23','Rua Tenente Benévolo, 200 - Meireles','ATIVO','CASADO','Pastoral','Presbítero'),
 ('22222222-0000-0000-0000-000000000016','11111111-1111-1111-1111-111111111111','Camila Duarte','camila.duarte@exemplo.com','(88) 99900-0016','2003-05-02','Rua Nogueira Acioli, 30 - Centro','ATIVO','SOLTEIRO','Jovens',NULL),
 ('22222222-0000-0000-0000-000000000017','11111111-1111-1111-1111-111111111111','Wesley Pinheiro','wesley.pinheiro@exemplo.com','(88) 99900-0017','1993-01-28','Av. Bezerra de Menezes, 890 - São Gerardo','ATIVO','SOLTEIRO','Louvor',NULL),
 ('22222222-0000-0000-0000-000000000018','11111111-1111-1111-1111-111111111111','Tereza Cristina Alves','tereza.alves@exemplo.com','(88) 99900-0018','1972-09-15','Rua Solon Pinheiro, 55 - Centro','ATIVO','CASADO','Diaconia',NULL),
 ('22222222-0000-0000-0000-000000000019','11111111-1111-1111-1111-111111111111','Gustavo Bezerra','gustavo.bezerra@exemplo.com','(88) 99900-0019','1986-04-19','Rua Silva Jatahy, 700 - Meireles','INATIVO','CASADO',NULL,'Mudou-se para outra cidade em 2025'),
 ('22222222-0000-0000-0000-000000000020','11111111-1111-1111-1111-111111111111','Fernanda Queiroz','fernanda.queiroz@exemplo.com','(88) 99900-0020','1994-08-07','Rua Antônio Pompeu, 145 - Centro','INATIVO','SOLTEIRO',NULL,NULL),
 ('22222222-0000-0000-0000-000000000021','11111111-1111-1111-1111-111111111111','Hélio Marinho','helio.marinho@exemplo.com','(88) 99900-0021','1958-12-01','Rua Guilherme Rocha, 88 - Centro','INATIVO','VIUVO',NULL,NULL),
 ('22222222-0000-0000-0000-000000000022','11111111-1111-1111-1111-111111111111','Bianca Moreira','bianca.moreira@exemplo.com','(88) 99900-0022','2000-02-14','Av. da Universidade, 2200 - Benfica','VISITANTE','SOLTEIRO',NULL,'Convidada por Camila Duarte'),
 ('22222222-0000-0000-0000-000000000023','11111111-1111-1111-1111-111111111111','Anderson Luz','anderson.luz@exemplo.com','(88) 99900-0023','1991-06-26','Rua Visconde do Rio Branco, 1010 - Centro','VISITANTE','CASADO',NULL,NULL),
 ('22222222-0000-0000-0000-000000000024','11111111-1111-1111-1111-111111111111','Rita de Cássia Nobre','rita.nobre@exemplo.com','(88) 99900-0024','1980-10-30','Rua Ana Bilhar, 400 - Varjota','VISITANTE','DIVORCIADO',NULL,'Primeira visita no culto de aniversário'),
 ('22222222-0000-0000-0000-000000000025','11111111-1111-1111-1111-111111111111','Samuel Ferreira Braga','samuel.braga@exemplo.com','(88) 99900-0025','1996-03-08','Rua Carlos Vasconcelos, 620 - Aldeota','ATIVO','SOLTEIRO','Ensino',NULL);

-- ─── Usuários ───────────────────────────────────────────────────────────────
-- Senha de TODOS os três: domus123   (hash BCrypt, custo 10)
INSERT INTO usuario (id, igreja_id, membro_id, role_id, senha_hash, ativo, ultimo_login_em)
SELECT u.id, '11111111-1111-1111-1111-111111111111', u.membro_id, r.id,
       '$2a$10$JHPEtW22ZNTEjJRALVRY1eSm7qCvYo5YtTOuK9s7ghiWAmADRowD.',
       TRUE, NOW() - (u.dias_atras || ' days')::interval
FROM (VALUES
        ('33333333-0000-0000-0000-000000000001'::uuid,'22222222-0000-0000-0000-000000000001'::uuid,'ADMIN_IGREJA',0),
        ('33333333-0000-0000-0000-000000000002'::uuid,'22222222-0000-0000-0000-000000000002'::uuid,'LIDER',2),
        ('33333333-0000-0000-0000-000000000003'::uuid,'22222222-0000-0000-0000-000000000003'::uuid,'MEMBRO',9)
     ) AS u(id, membro_id, role_nome, dias_atras)
JOIN role r ON r.nome = u.role_nome;

-- ─── Eventos ────────────────────────────────────────────────────────────────
-- Mistura de eventos passados e futuros, para a agenda ter os dois lados.
INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local)
VALUES
 ('44444444-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','Culto de Celebração','Culto dominical da manhã, com participação do coral.',(CURRENT_DATE - 21) + TIME '09:00',(CURRENT_DATE - 21) + TIME '11:00','Templo Principal'),
 ('44444444-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','Escola Bíblica Dominical','Estudo no livro de Efésios, turmas por faixa etária.',(CURRENT_DATE - 14) + TIME '08:00',(CURRENT_DATE - 14) + TIME '09:30','Salas de Ensino'),
 ('44444444-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','Reunião de Liderança','Alinhamento do calendário do semestre e prestação de contas.',(CURRENT_DATE - 10) + TIME '19:30',(CURRENT_DATE - 10) + TIME '21:00','Sala de Reuniões'),
 ('44444444-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','Ação Social no Bairro','Distribuição de cestas básicas e corte de cabelo gratuito.',(CURRENT_DATE - 7) + TIME '14:00',(CURRENT_DATE - 7) + TIME '18:00','Praça da Matriz'),
 ('44444444-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','Culto de Oração','Momento de intercessão pelas famílias da igreja.',(CURRENT_DATE - 3) + TIME '19:00',(CURRENT_DATE - 3) + TIME '20:30','Templo Principal'),
 ('44444444-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','Ensaio do Ministério de Louvor','Preparação do repertório do próximo domingo.',(CURRENT_DATE + 1) + TIME '19:00',(CURRENT_DATE + 1) + TIME '21:00','Templo Principal'),
 ('44444444-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','Culto de Celebração','Culto dominical da manhã.',(CURRENT_DATE + 4) + TIME '09:00',(CURRENT_DATE + 4) + TIME '11:00','Templo Principal'),
 ('44444444-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','Encontro de Jovens','Noite de louvor e mensagem voltada ao público jovem.',(CURRENT_DATE + 9) + TIME '19:00',(CURRENT_DATE + 9) + TIME '22:00','Salão Social'),
 ('44444444-0000-0000-0000-000000000009','11111111-1111-1111-1111-111111111111','Batismo nas Águas','Cerimônia de batismo com 8 candidatos.',(CURRENT_DATE + 16) + TIME '16:00',(CURRENT_DATE + 16) + TIME '18:00','Templo Principal'),
 ('44444444-0000-0000-0000-000000000010','11111111-1111-1111-1111-111111111111','Congresso de Famílias','Três dias de palestras com preletor convidado.',(CURRENT_DATE + 30) + TIME '18:00',(CURRENT_DATE + 32) + TIME '21:00','Templo Principal'),
 ('44444444-0000-0000-0000-000000000011','11111111-1111-1111-1111-111111111111','Aniversário da Igreja','Celebração dos 42 anos com almoço comunitário.',(CURRENT_DATE + 45) + TIME '10:00',(CURRENT_DATE + 45) + TIME '15:00','Salão Social');

-- ─── Categorias financeiras ─────────────────────────────────────────────────
INSERT INTO categoria_financeira (id, igreja_id, nome, tipo) VALUES
 ('55555555-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','Dízimos','ENTRADA'),
 ('55555555-0000-0000-0000-000000000002','11111111-1111-1111-1111-111111111111','Ofertas','ENTRADA'),
 ('55555555-0000-0000-0000-000000000003','11111111-1111-1111-1111-111111111111','Doações para Missões','ENTRADA'),
 ('55555555-0000-0000-0000-000000000004','11111111-1111-1111-1111-111111111111','Aluguel do Templo','SAIDA'),
 ('55555555-0000-0000-0000-000000000005','11111111-1111-1111-1111-111111111111','Energia e Água','SAIDA'),
 ('55555555-0000-0000-0000-000000000006','11111111-1111-1111-1111-111111111111','Manutenção e Reformas','SAIDA'),
 ('55555555-0000-0000-0000-000000000007','11111111-1111-1111-1111-111111111111','Ação Social','SAIDA'),
 ('55555555-0000-0000-0000-000000000008','11111111-1111-1111-1111-111111111111','Equipamentos de Som','AMBOS');

-- ─── Movimentações financeiras ──────────────────────────────────────────────
-- Seis meses fechados + o mês corrente, para os relatórios terem série histórica.

-- Dízimos: um lançamento por membro dizimista, por mês.
INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                     membro_id, tipo, valor, data_movimentacao, descricao)
SELECT '11111111-1111-1111-1111-111111111111',
       '55555555-0000-0000-0000-000000000001',
       '33333333-0000-0000-0000-000000000001',
       m.id,
       'ENTRADA',
       ROUND((120 + (RANDOM() * 380))::numeric, 2),
       (date_trunc('month', CURRENT_DATE) - (mes || ' months')::interval
         + ((5 + FLOOR(RANDOM() * 20)) || ' days')::interval)::date,
       'Dízimo mensal'
FROM generate_series(0, 6) AS mes
CROSS JOIN (
    SELECT id FROM membro
    WHERE status = 'ATIVO'
      AND igreja_id = '11111111-1111-1111-1111-111111111111'
    LIMIT 12
) AS m;

-- Ofertas: uma por culto dominical (4 por mês), sem membro vinculado.
INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                     tipo, valor, data_movimentacao, descricao)
SELECT '11111111-1111-1111-1111-111111111111',
       '55555555-0000-0000-0000-000000000002',
       '33333333-0000-0000-0000-000000000001',
       'ENTRADA',
       ROUND((300 + (RANDOM() * 700))::numeric, 2),
       (date_trunc('month', CURRENT_DATE) - (mes || ' months')::interval
         + ((semana * 7) || ' days')::interval)::date,
       'Oferta do culto de domingo'
FROM generate_series(0, 6) AS mes
CROSS JOIN generate_series(0, 3) AS semana;

-- Despesas fixas mensais.
INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                     tipo, valor, data_movimentacao, descricao)
SELECT '11111111-1111-1111-1111-111111111111',
       d.categoria_id,
       '33333333-0000-0000-0000-000000000001',
       'SAIDA',
       ROUND((d.base + (RANDOM() * d.variacao))::numeric, 2),
       (date_trunc('month', CURRENT_DATE) - (mes || ' months')::interval
         + (d.dia || ' days')::interval)::date,
       d.descricao
FROM generate_series(0, 6) AS mes
CROSS JOIN (VALUES
      ('55555555-0000-0000-0000-000000000004'::uuid, 2200, 0,   4, 'Aluguel do templo'),
      ('55555555-0000-0000-0000-000000000005'::uuid,  480, 260, 9, 'Conta de energia e água')
     ) AS d(categoria_id, base, variacao, dia, descricao);

-- Lançamentos pontuais, para o extrato não parecer gerado por script.
INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                     membro_id, tipo, valor, data_movimentacao, descricao)
VALUES
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000003','33333333-0000-0000-0000-000000000001','22222222-0000-0000-0000-000000000015','ENTRADA',5000.00,CURRENT_DATE - 52,'Doação para o campo missionário do Nordeste'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000003','33333333-0000-0000-0000-000000000001','22222222-0000-0000-0000-000000000010','ENTRADA',1500.00,CURRENT_DATE - 25,'Oferta missionária'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000006','33333333-0000-0000-0000-000000000001',NULL,'SAIDA',3800.00,CURRENT_DATE - 63,'Reforma do telhado do salão social'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000006','33333333-0000-0000-0000-000000000001',NULL,'SAIDA',940.00,CURRENT_DATE - 18,'Troca da fiação elétrica das salas de ensino'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000007','33333333-0000-0000-0000-000000000001',NULL,'SAIDA',2150.00,CURRENT_DATE - 7,'Cestas básicas da ação social no bairro'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000008','33333333-0000-0000-0000-000000000001',NULL,'SAIDA',6400.00,CURRENT_DATE - 40,'Compra de mesa de som digital'),
 ('11111111-1111-1111-1111-111111111111','55555555-0000-0000-0000-000000000008','33333333-0000-0000-0000-000000000001',NULL,'ENTRADA',800.00,CURRENT_DATE - 33,'Venda da mesa de som antiga');

-- ─── Outbox: enfileira tudo para indexação no Elasticsearch ─────────────────
-- O OutboxProcessador roda a cada 3s e consome estes eventos enquanto a API
-- estiver de pé. Nada aqui vai para o ES se a aplicação estiver parada.
INSERT INTO outbox (tipo_entidade, tipo_evento, entidade_id, igreja_id)
SELECT 'MEMBRO', 'CRIADO', id, igreja_id FROM membro
UNION ALL SELECT 'EVENTO', 'CRIADO', id, igreja_id FROM evento
UNION ALL SELECT 'USUARIO', 'CRIADO', id, igreja_id FROM usuario
UNION ALL SELECT 'CATEGORIA', 'CRIADO', id, igreja_id FROM categoria_financeira
UNION ALL SELECT 'MOVIMENTACAO', 'CRIADO', id, igreja_id FROM movimentacao_financeira;

COMMIT;

-- ─── Resumo ─────────────────────────────────────────────────────────────────
SELECT 'igreja' AS tabela, COUNT(*) FROM igreja
UNION ALL SELECT 'membro', COUNT(*) FROM membro
UNION ALL SELECT 'usuario', COUNT(*) FROM usuario
UNION ALL SELECT 'evento', COUNT(*) FROM evento
UNION ALL SELECT 'categoria_financeira', COUNT(*) FROM categoria_financeira
UNION ALL SELECT 'movimentacao_financeira', COUNT(*) FROM movimentacao_financeira
UNION ALL SELECT 'outbox (pendente)', COUNT(*) FROM outbox WHERE processado = FALSE;
