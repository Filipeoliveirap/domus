-- =====================================================================
-- seed-dev.sql — dados de demonstração para a apresentação à liderança
-- =====================================================================
--
-- IDEMPOTENTE: seguro rodar de novo. O script primeiro APAGA (em ordem que
-- respeita as FKs) tudo que pertence às 4 igrejas de UUID fixo abaixo, e só
-- depois reinsere. Rodar duas vezes produz o mesmo estado final, não
-- duplica nada.
--
-- Para limpar manualmente sem reinserir, rode só o bloco "LIMPEZA" (linha
-- ~30 até a inserção das igrejas) e não rode o resto.
--
-- Estrutura: 1 sede + 3 congregações vinculadas (igreja.igreja_mae_id),
-- respeitando a regra dos 2 níveis (quem tem mãe não pode ser mãe).
--
-- Senha de TODAS as contas de demo: 12345678
-- Hash bcrypt (já verificado contra o BCryptPasswordEncoder do projeto):
--   $2b$10$M67QXiBCzKQeG4oeLiIq4OYjkQ7rv.7Nktptf0EIyAZ47g7nd2epa
--
-- Este arquivo assume que a migration V1 já rodou (roles ADMIN_IGREJA,
-- LIDER, ACESSO_COMUM já existem).

BEGIN;

-- ---------------------------------------------------------------------
-- LIMPEZA — apaga qualquer estado anterior destas 4 igrejas de demo,
-- na ordem que a FK exige (filhos antes de pais).
-- ---------------------------------------------------------------------
DO $$
DECLARE
    ids uuid[] := ARRAY[
        'a0000000-0000-0000-0000-000000000001'::uuid, -- sede
        'a0000000-0000-0000-0000-000000000002'::uuid, -- congregação 1
        'a0000000-0000-0000-0000-000000000003'::uuid, -- congregação 2
        'a0000000-0000-0000-0000-000000000004'::uuid  -- congregação 3
    ];
BEGIN
    DELETE FROM acompanhante_inscricao
     WHERE inscricao_id IN (SELECT id FROM inscricao_evento WHERE igreja_id = ANY(ids));
    DELETE FROM inscricao_evento     WHERE igreja_id = ANY(ids);
    DELETE FROM movimentacao_financeira WHERE igreja_id = ANY(ids);
    DELETE FROM categoria_financeira WHERE igreja_id = ANY(ids);
    DELETE FROM evento               WHERE igreja_id = ANY(ids);
    DELETE FROM usuario              WHERE igreja_id = ANY(ids);
    DELETE FROM pessoa                WHERE igreja_id = ANY(ids);
    DELETE FROM outbox               WHERE igreja_id = ANY(ids);
    -- quebra as auto-referências antes de apagar a linha da igreja
    UPDATE igreja SET igreja_mae_id = NULL, atualizado_por_usuario_id = NULL,
                       vinculado_por_usuario_id = NULL
     WHERE id = ANY(ids);
    DELETE FROM igreja WHERE id = ANY(ids);
END $$;

-- ---------------------------------------------------------------------
-- IGREJAS — 1 sede + 3 congregações
-- ---------------------------------------------------------------------
INSERT INTO igreja (id, nome, cnpj, email, telefone, plano, igreja_mae_id,
                     cep, logradouro, numero, complemento, bairro, cidade, uf,
                     denominacao)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Igreja Batista Central',
     '12.345.678/0001-90', 'contato@batistacentral.org.br', '(11) 3456-7890', 'PREMIUM',
     NULL, '01310-100', 'Av. Paulista', '1000', 'Sala 12', 'Bela Vista', 'São Paulo', 'SP',
     'Batista');

INSERT INTO igreja (id, nome, cnpj, email, telefone, plano, igreja_mae_id,
                     cep, logradouro, numero, complemento, bairro, cidade, uf,
                     denominacao)
VALUES
    ('a0000000-0000-0000-0000-000000000002', 'Igreja Batista Central - Congregação Vila Nova',
     '12.345.678/0002-71', 'vilanova@batistacentral.org.br', '(11) 3456-1111', 'PREMIUM',
     'a0000000-0000-0000-0000-000000000001',
     '04101-000', 'Rua Vergueiro', '2200', NULL, 'Vila Mariana', 'São Paulo', 'SP', 'Batista'),
    ('a0000000-0000-0000-0000-000000000003', 'Igreja Batista Central - Congregação Jardim das Flores',
     '12.345.678/0003-52', 'jardimdasflores@batistacentral.org.br', '(11) 3456-2222', 'PREMIUM',
     'a0000000-0000-0000-0000-000000000001',
     '05704-000', 'Av. Giovanni Gronchi', '500', NULL, 'Morumbi', 'São Paulo', 'SP', 'Batista'),
    ('a0000000-0000-0000-0000-000000000004', 'Igreja Batista Central - Congregação Ipiranga',
     '12.345.678/0004-33', 'ipiranga@batistacentral.org.br', '(11) 3456-3333', 'PREMIUM',
     'a0000000-0000-0000-0000-000000000001',
     '04202-000', 'Rua Silva Bueno', '300', NULL, 'Ipiranga', 'São Paulo', 'SP', 'Batista');

-- ---------------------------------------------------------------------
-- PESSOAS — mistura de MEMBRO e CONGREGANTE em cada igreja, gerada por
-- laço (nomes/sobrenomes brasileiros combinados aleatoriamente).
-- Guardamos os ids gerados numa tabela temporária para usar depois em
-- eventos, inscrições e financeiro.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_pessoas (
    igreja_id uuid,
    pessoa_id uuid,
    vinculo   varchar(20),
    papel     varchar(20) -- 'FILLER' ou 'CONTA' (tem usuário)
) ON COMMIT DROP;

DO $$
DECLARE
    nomes text[] := ARRAY['João','Maria','José','Ana','Pedro','Paula','Lucas','Fernanda',
        'Carlos','Juliana','Marcos','Camila','Rafael','Beatriz','Gabriel','Larissa','Bruno',
        'Patrícia','Thiago','Aline','Rodrigo','Vanessa','Diego','Renata','Felipe','Cristina',
        'André','Débora','Eduardo','Simone','Gustavo','Priscila','Leonardo','Tatiane',
        'Vinícius','Michele','Daniel','Sandra','Fábio','Raquel','Ricardo','Elaine','Marcelo',
        'Adriana','Rogério','Cláudia','Sérgio','Luciana'];
    sobrenomes text[] := ARRAY['Silva','Santos','Oliveira','Souza','Rodrigues','Ferreira',
        'Alves','Pereira','Lima','Gomes','Costa','Ribeiro','Martins','Carvalho','Almeida',
        'Lopes','Soares','Fernandes','Vieira','Barbosa','Araújo','Melo','Barros','Freitas',
        'Cardoso'];
    ministerios text[] := ARRAY['Louvor','Intercessão','Infantil','Diaconato','Recepção',
        'Mídia','Ação Social', NULL, NULL];
    estados_civis text[] := ARRAY['SOLTEIRO','CASADO','DIVORCIADO','VIUVO'];
    bairros text[] := ARRAY['Centro','Jardim América','Vila Nova','Boa Vista','Bela Vista'];
    igrejas uuid[] := ARRAY['a0000000-0000-0000-0000-000000000001'::uuid,
                            'a0000000-0000-0000-0000-000000000002'::uuid,
                            'a0000000-0000-0000-0000-000000000003'::uuid,
                            'a0000000-0000-0000-0000-000000000004'::uuid];
    qtds int[] := ARRAY[32, 16, 14, 12]; -- 25-40 na sede, 10-20 nas congregações
    last_day_this_month int := extract(day from (date_trunc('month', CURRENT_DATE)
                                        + interval '1 month' - interval '1 day'))::int;
    ig uuid;
    n  int;
    idx int;
    j  int;
    pid uuid;
    vinc varchar(20);
    nasc date;
    batismo date;
    idade int;
BEGIN
    FOR idx IN 1 .. array_length(igrejas, 1) LOOP
        ig := igrejas[idx];
        n  := qtds[idx];
        FOR j IN 1 .. n LOOP
            vinc := CASE WHEN random() < 0.62 THEN 'MEMBRO' ELSE 'CONGREGANTE' END;

            -- Aniversariantes: 1 pessoa da sede faz aniversário HOJE; a
            -- cada 6ª pessoa de cada igreja, aniversário em algum outro
            -- dia do mês corrente; o resto, espalhado nos outros meses.
            idade := 18 + floor(random() * 55)::int;
            IF idx = 1 AND j = 1 THEN
                nasc := make_date(extract(year FROM CURRENT_DATE)::int - idade,
                                   extract(month FROM CURRENT_DATE)::int,
                                   extract(day FROM CURRENT_DATE)::int);
            ELSIF j % 6 = 0 THEN
                nasc := make_date(extract(year FROM CURRENT_DATE)::int - idade,
                                   extract(month FROM CURRENT_DATE)::int,
                                   1 + floor(random() * last_day_this_month)::int);
            ELSE
                nasc := CURRENT_DATE - (idade * 365 + floor(random() * 365)::int);
            END IF;

            -- data_batismo só existe para MEMBRO (o estado impossível que
            -- o modelo pessoa/vinculo elimina) e nunca no futuro.
            batismo := NULL;
            IF vinc = 'MEMBRO' AND random() < 0.85 THEN
                batismo := LEAST(nasc + ((14 + floor(random() * 20)::int) * 365), CURRENT_DATE - 30);
            END IF;

            pid := gen_random_uuid();
            INSERT INTO pessoa (id, igreja_id, nome, email, telefone, data_nascimento,
                                 estado_civil, ministerio, data_batismo, vinculo,
                                 cep, logradouro, numero, bairro, cidade, uf)
            VALUES (
                pid, ig,
                nomes[1 + floor(random() * array_length(nomes, 1))::int] || ' ' ||
                    sobrenomes[1 + floor(random() * array_length(sobrenomes, 1))::int],
                NULL, -- e-mail nulo nos figurantes: evita colisão com uq_pessoa_email
                '(11) 9' || lpad(floor(random() * 100000000)::text, 8, '0'),
                nasc,
                estados_civis[1 + floor(random() * array_length(estados_civis, 1))::int],
                ministerios[1 + floor(random() * array_length(ministerios, 1))::int],
                batismo, vinc,
                CASE WHEN random() < 0.7 THEN lpad(floor(random() * 99999999)::text, 8, '0') ELSE NULL END,
                CASE WHEN random() < 0.7 THEN 'Rua ' || sobrenomes[1 + floor(random() * array_length(sobrenomes,1))::int] ELSE NULL END,
                CASE WHEN random() < 0.7 THEN (1 + floor(random() * 2000))::text ELSE NULL END,
                CASE WHEN random() < 0.7 THEN bairros[1 + floor(random() * array_length(bairros,1))::int] ELSE NULL END,
                CASE WHEN random() < 0.7 THEN 'São Paulo' ELSE NULL END,
                CASE WHEN random() < 0.7 THEN 'SP' ELSE NULL END
            );
            INSERT INTO tmp_pessoas VALUES (ig, pid, vinc, 'FILLER');
        END LOOP;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- CONTAS DE DEMONSTRAÇÃO — pessoas específicas com login, para o autor
-- demonstrar cada perfil de acesso. Todas com a mesma senha (12345678).
-- ---------------------------------------------------------------------
INSERT INTO pessoa (id, igreja_id, nome, email, telefone, data_nascimento, estado_civil,
                     ministerio, data_batismo, vinculo)
VALUES
    -- ADMIN_IGREJA da sede — a conta pessoal do autor
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
     'José Filipe Oliveira Pereira', 'josefilipe.dev@gmail.com', '(11) 98888-0001',
     '1998-03-15', 'SOLTEIRO', 'Diaconato', '2015-06-01', 'MEMBRO'),
    -- LIDER da sede
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
     'Ricardo Almeida Souza', 'lider1@gmail.com', '(11) 98888-0002',
     '1985-07-22', 'CASADO', 'Louvor', '2005-04-10', 'MEMBRO'),
    -- ACESSO_COMUM da sede (congregante com login)
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001',
     'Beatriz Carvalho Lima', 'membro1@gmail.com', '(11) 98888-0003',
     '1999-11-05', 'SOLTEIRO', NULL, NULL, 'CONGREGANTE'),
    -- ADMIN_IGREJA de cada congregação
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000002',
     'Marcos Vinícius Ferreira', 'congregacao1@gmail.com', '(11) 98888-0004',
     '1980-02-10', 'CASADO', 'Diaconato', '2000-01-15', 'MEMBRO'),
    ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000003',
     'Camila Santos Rodrigues', 'congregacao2@gmail.com', '(11) 98888-0005',
     '1982-09-30', 'CASADO', 'Diaconato', '2001-05-20', 'MEMBRO'),
    ('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000004',
     'Eduardo Barbosa Melo', 'congregacao3@gmail.com', '(11) 98888-0006',
     '1978-12-01', 'CASADO', 'Diaconato', '1999-08-08', 'MEMBRO'),
    -- LIDER e ACESSO_COMUM na congregação 1, para variar onde o demo mostra os perfis
    ('b0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000002',
     'Larissa Gomes Pereira', 'lider2@gmail.com', '(11) 98888-0007',
     '1990-05-18', 'SOLTEIRO', 'Recepção', '2010-03-01', 'MEMBRO'),
    ('b0000000-0000-0000-0000-000000000008', 'a0000000-0000-0000-0000-000000000002',
     'Gustavo Lopes Araújo', 'membro2@gmail.com', '(11) 98888-0008',
     '2001-08-25', 'SOLTEIRO', NULL, NULL, 'CONGREGANTE');

INSERT INTO tmp_pessoas
SELECT igreja_id, id, vinculo, 'CONTA' FROM pessoa
 WHERE id IN (
    'b0000000-0000-0000-0000-000000000001','b0000000-0000-0000-0000-000000000002',
    'b0000000-0000-0000-0000-000000000003','b0000000-0000-0000-0000-000000000004',
    'b0000000-0000-0000-0000-000000000005','b0000000-0000-0000-0000-000000000006',
    'b0000000-0000-0000-0000-000000000007','b0000000-0000-0000-0000-000000000008');

-- ---------------------------------------------------------------------
-- USUÁRIOS — credenciais para as 8 pessoas acima. Hash bcrypt de
-- "12345678" verificado contra o BCryptPasswordEncoder do projeto.
-- ---------------------------------------------------------------------
INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, senha_hash, ativo)
SELECT gen_random_uuid(), p.igreja_id, p.id, r.id,
       '$2b$10$M67QXiBCzKQeG4oeLiIq4OYjkQ7rv.7Nktptf0EIyAZ47g7nd2epa', true
FROM pessoa p
JOIN role r ON r.nome = CASE p.email
    WHEN 'josefilipe.dev@gmail.com' THEN 'ADMIN_IGREJA'
    WHEN 'lider1@gmail.com'         THEN 'LIDER'
    WHEN 'membro1@gmail.com'        THEN 'ACESSO_COMUM'
    WHEN 'congregacao1@gmail.com'   THEN 'ADMIN_IGREJA'
    WHEN 'congregacao2@gmail.com'   THEN 'ADMIN_IGREJA'
    WHEN 'congregacao3@gmail.com'   THEN 'ADMIN_IGREJA'
    WHEN 'lider2@gmail.com'         THEN 'LIDER'
    WHEN 'membro2@gmail.com'        THEN 'ACESSO_COMUM'
END
WHERE p.email IN ('josefilipe.dev@gmail.com','lider1@gmail.com','membro1@gmail.com',
                   'congregacao1@gmail.com','congregacao2@gmail.com','congregacao3@gmail.com',
                   'lider2@gmail.com','membro2@gmail.com');

-- ---------------------------------------------------------------------
-- EVENTOS — passados, em andamento e futuros, com combinações de
-- inscrição/preço/exclusividade, em cada igreja.
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_eventos (
    igreja_id uuid,
    evento_id uuid,
    requer_inscricao boolean,
    vagas int
) ON COMMIT DROP;

DO $$
DECLARE
    igrejas uuid[] := ARRAY['a0000000-0000-0000-0000-000000000001'::uuid,
                            'a0000000-0000-0000-0000-000000000002'::uuid,
                            'a0000000-0000-0000-0000-000000000003'::uuid,
                            'a0000000-0000-0000-0000-000000000004'::uuid];
    ig uuid;
    eid uuid;
BEGIN
    FOREACH ig IN ARRAY igrejas LOOP
        -- 1) evento já finalizado, sem inscrição (culto especial)
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Culto de Ação de Graças',
                'Culto especial de gratidão pelo semestre.',
                NOW() - interval '45 days', NOW() - interval '45 days' + interval '2 hours',
                'Templo principal', false, false);

        -- 2) conferência já finalizada, COM inscrição e vagas esgotadas
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             vagas, preco, requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Conferência de Casais 2026',
                'Encontro anual para casais, com palestras e dinâmicas.',
                NOW() - interval '20 days', NOW() - interval '20 days' + interval '6 hours',
                'Salão social', 20, 45.00, true, false);
        INSERT INTO tmp_eventos VALUES (ig, eid, true, 20);

        -- 3) evento em andamento agora (acontecendo hoje), gratuito
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Semana de Oração',
                'Programação diária de oração e jejum.',
                NOW() - interval '1 day', NOW() + interval '4 days',
                'Templo principal', false, false);

        -- 4) evento futuro próximo, com inscrição e vagas, aberto a todos
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             vagas, requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Retiro de Jovens',
                'Final de semana de retiro com a juventude.',
                NOW() + interval '15 days', NOW() + interval '17 days',
                'Chácara Monte Sião', 30, true, false);
        INSERT INTO tmp_eventos VALUES (ig, eid, true, 30);

        -- 5) evento futuro pago, exclusivo para membros batizados
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             vagas, preco, requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Jantar de Comunhão dos Membros',
                'Jantar anual restrito aos membros batizados da igreja.',
                NOW() + interval '30 days', NOW() + interval '30 days' + interval '3 hours',
                'Salão social', 25, 30.00, true, true);
        INSERT INTO tmp_eventos VALUES (ig, eid, true, 25);

        -- 6) evento futuro distante, sem inscrição (só divulgação)
        eid := gen_random_uuid();
        INSERT INTO evento (id, igreja_id, titulo, descricao, inicio_em, fim_em, local,
                             requer_inscricao, exclusivo_membros)
        VALUES (eid, ig, 'Culto de Natal', 'Celebração de Natal da igreja.',
                NOW() + interval '150 days', NOW() + interval '150 days' + interval '2 hours',
                'Templo principal', false, false);
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- INSCRIÇÕES — respeita UNIQUE(evento_id, pessoa_id) e nunca deixa
-- confirmados + acompanhantes passarem de `vagas`.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    ev RECORD;
    pessoa_ids uuid[];
    elegiveis  uuid[]; -- para o evento exclusivo p/ membros, só MEMBRO
    total_confirmados int;
    qtd_inscritos int;
    p uuid;
    insc_id uuid;
    k int;
    status_insc varchar(20);
BEGIN
    FOR ev IN SELECT te.evento_id, te.igreja_id, te.vagas, e.exclusivo_membros
              FROM tmp_eventos te JOIN evento e ON e.id = te.evento_id
    LOOP
        IF ev.exclusivo_membros THEN
            SELECT array_agg(pessoa_id) INTO elegiveis FROM tmp_pessoas
             WHERE igreja_id = ev.igreja_id AND vinculo = 'MEMBRO';
        ELSE
            SELECT array_agg(pessoa_id) INTO elegiveis FROM tmp_pessoas
             WHERE igreja_id = ev.igreja_id;
        END IF;

        IF elegiveis IS NULL OR array_length(elegiveis, 1) = 0 THEN
            CONTINUE;
        END IF;

        -- Reserva 2 vagas de folga para não estourar `vagas` com acompanhantes.
        qtd_inscritos := LEAST(array_length(elegiveis, 1), ev.vagas - 2);
        IF qtd_inscritos < 1 THEN
            qtd_inscritos := 1;
        END IF;

        total_confirmados := 0;
        FOR k IN 1 .. qtd_inscritos LOOP
            p := elegiveis[k];
            -- 1 em cada 8 inscrições vira CANCELADA (não ocupa vaga)
            status_insc := CASE WHEN k % 8 = 0 THEN 'CANCELADA' ELSE 'CONFIRMADA' END;
            insc_id := gen_random_uuid();
            INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status)
            VALUES (insc_id, ev.igreja_id, ev.evento_id, p, status_insc);

            IF status_insc = 'CONFIRMADA' THEN
                total_confirmados := total_confirmados + 1;
                -- as duas primeiras confirmadas de cada evento levam 1 acompanhante
                IF k <= 2 AND total_confirmados + 1 <= ev.vagas THEN
                    INSERT INTO acompanhante_inscricao (inscricao_id, nome, telefone)
                    VALUES (insc_id, 'Acompanhante de ' || k, '(11) 97777-0000');
                    total_confirmados := total_confirmados + 1; -- acompanhante também ocupa vaga
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- CATEGORIAS FINANCEIRAS — mesmo conjunto em cada igreja (nome único
-- por igreja, então não colide entre elas).
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_categorias (
    igreja_id uuid,
    categoria_id uuid,
    tipo varchar(20)
) ON COMMIT DROP;

DO $$
DECLARE
    igrejas uuid[] := ARRAY['a0000000-0000-0000-0000-000000000001'::uuid,
                            'a0000000-0000-0000-0000-000000000002'::uuid,
                            'a0000000-0000-0000-0000-000000000003'::uuid,
                            'a0000000-0000-0000-0000-000000000004'::uuid];
    cats record;
    ig uuid;
    cid uuid;
BEGIN
    FOREACH ig IN ARRAY igrejas LOOP
        FOR cats IN SELECT * FROM (VALUES
            ('Dízimos', 'ENTRADA'), ('Ofertas', 'ENTRADA'), ('Doações', 'ENTRADA'),
            ('Eventos', 'AMBOS'),
            ('Aluguel/Manutenção', 'SAIDA'), ('Energia e Água', 'SAIDA'),
            ('Missões', 'SAIDA'), ('Material de Escritório', 'SAIDA')
        ) AS t(nome, tipo)
        LOOP
            cid := gen_random_uuid();
            INSERT INTO categoria_financeira (id, igreja_id, nome, tipo)
            VALUES (cid, ig, cats.nome, cats.tipo);
            INSERT INTO tmp_categorias VALUES (ig, cid, cats.tipo);
        END LOOP;
    END LOOP;
END $$;

-- ---------------------------------------------------------------------
-- MOVIMENTAÇÕES FINANCEIRAS — 6 meses de histórico em cada igreja, com
-- variação plausível, para o relatório consolidado ter números reais.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    igrejas uuid[] := ARRAY['a0000000-0000-0000-0000-000000000001'::uuid,
                            'a0000000-0000-0000-0000-000000000002'::uuid,
                            'a0000000-0000-0000-0000-000000000003'::uuid,
                            'a0000000-0000-0000-0000-000000000004'::uuid];
    admin_por_igreja jsonb := '{
        "a0000000-0000-0000-0000-000000000001": "b0000000-0000-0000-0000-000000000001",
        "a0000000-0000-0000-0000-000000000002": "b0000000-0000-0000-0000-000000000004",
        "a0000000-0000-0000-0000-000000000003": "b0000000-0000-0000-0000-000000000005",
        "a0000000-0000-0000-0000-000000000004": "b0000000-0000-0000-0000-000000000006"
    }';
    ig uuid;
    usuario_criador uuid;
    mes_offset int;
    dia int;
    data_mov date;
    entradas_cat uuid[];
    saidas_cat uuid[];
    ambos_cat uuid[];
    cat uuid;
    valor numeric;
    pessoa_ref uuid;
    n_lancamentos int;
    i int;
    descricoes_entrada text[] := ARRAY['Dízimo mensal','Oferta de gratidão','Doação campanha',
        'Contribuição especial'];
    descricoes_saida text[] := ARRAY['Conta de energia','Conta de água','Manutenção predial',
        'Material de limpeza','Apoio a missionário','Compra de material de escritório'];
BEGIN
    FOREACH ig IN ARRAY igrejas LOOP
        SELECT (admin_por_igreja ->> ig::text)::uuid INTO usuario_criador;
        -- pega o usuario.id correspondente à pessoa admin desta igreja
        SELECT u.id INTO usuario_criador FROM usuario u WHERE u.pessoa_id = usuario_criador;

        SELECT array_agg(categoria_id) INTO entradas_cat FROM tmp_categorias WHERE igreja_id = ig AND tipo = 'ENTRADA';
        SELECT array_agg(categoria_id) INTO saidas_cat   FROM tmp_categorias WHERE igreja_id = ig AND tipo = 'SAIDA';
        SELECT array_agg(categoria_id) INTO ambos_cat    FROM tmp_categorias WHERE igreja_id = ig AND tipo = 'AMBOS';

        FOR mes_offset IN 0 .. 5 LOOP -- últimos 6 meses, incluindo o atual
            data_mov := (date_trunc('month', CURRENT_DATE) - (mes_offset || ' months')::interval)::date;

            -- ~14 entradas de dízimos/ofertas no mês, valores plausíveis
            n_lancamentos := 10 + floor(random() * 8)::int;
            FOR i IN 1 .. n_lancamentos LOOP
                dia := 1 + floor(random() * 27)::int;
                cat := entradas_cat[1 + floor(random() * array_length(entradas_cat, 1))::int];
                valor := round((50 + random() * 950)::numeric, 2);
                -- metade das entradas atribuída a uma pessoa (o contribuinte)
                SELECT pessoa_id INTO pessoa_ref FROM tmp_pessoas WHERE igreja_id = ig ORDER BY random() LIMIT 1;
                IF random() < 0.5 THEN pessoa_ref := NULL; END IF;

                INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                                       pessoa_id, tipo, valor, data_movimentacao, descricao)
                VALUES (ig, cat, usuario_criador, pessoa_ref, 'ENTRADA', valor,
                        make_date(extract(year from data_mov)::int, extract(month from data_mov)::int, dia),
                        descricoes_entrada[1 + floor(random() * array_length(descricoes_entrada,1))::int]);
            END LOOP;

            -- ~7 saídas fixas/variáveis no mês
            n_lancamentos := 5 + floor(random() * 5)::int;
            FOR i IN 1 .. n_lancamentos LOOP
                dia := 1 + floor(random() * 27)::int;
                cat := saidas_cat[1 + floor(random() * array_length(saidas_cat, 1))::int];
                valor := round((80 + random() * 1200)::numeric, 2);

                INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                                       pessoa_id, tipo, valor, data_movimentacao, descricao)
                VALUES (ig, cat, usuario_criador, NULL, 'SAIDA', valor,
                        make_date(extract(year from data_mov)::int, extract(month from data_mov)::int, dia),
                        descricoes_saida[1 + floor(random() * array_length(descricoes_saida,1))::int]);
            END LOOP;

            -- 1 entrada de "Eventos" (categoria AMBOS) por mês, se existir
            IF ambos_cat IS NOT NULL AND array_length(ambos_cat, 1) > 0 THEN
                dia := 1 + floor(random() * 27)::int;
                valor := round((200 + random() * 600)::numeric, 2);
                INSERT INTO movimentacao_financeira (igreja_id, categoria_id, criado_por_usuario_id,
                                                       pessoa_id, tipo, valor, data_movimentacao, descricao)
                VALUES (ig, ambos_cat[1], usuario_criador, NULL, 'ENTRADA', valor,
                        make_date(extract(year from data_mov)::int, extract(month from data_mov)::int, dia),
                        'Arrecadação de evento do mês');
            END IF;
        END LOOP;
    END LOOP;
END $$;

COMMIT;
