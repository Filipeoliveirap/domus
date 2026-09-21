-- V44__habilita_pg_stat_statements.sql
-- Habilita a extensão pg_stat_statements no Neon para que o painel
-- "Monitoring → Query performance" consiga mostrar as queries mais executadas
-- e o tempo total gasto em cada uma. No Neon o ideal é instalar via
-- CREATE EXTENSION IF NOT EXISTS, porque a vinda por default depende do plano.
--
-- Esta migration é defensiva: se a extensão já estiver ativa (alguns planos
-- do Neon a instalam por padrão), o IF NOT EXISTS evita erro na migration.

CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
