-- Garantia NO BANCO da regra dos 2 níveis (quem tem mãe não pode ser mãe).
--
-- POR QUE existir, se o VinculoService já valida e já trava as linhas:
-- a validação da aplicação vale enquanto TODA escrita passar pelo VinculoService. Ela é uma
-- garantia de disciplina, não do sistema. Um endpoint administrativo futuro, um script de
-- correção ou um import em massa reintroduz o furo em silêncio.
--
-- O QUE o furo causa: com igreja A apontando para B e B apontando para A, a família de cada
-- uma passa a conter a outra, e FamiliaIgrejaService.pertenceAFamilia() aprova NOS DOIS
-- SENTIDOS — cada igreja lê o financeiro da outra. É o pior modo de falha do produto, e é
-- silencioso: nenhuma tela mostra o estado inválido.
--
-- Regra que este arquivo aplica: invariante que, se quebrar, vaza dado entre inquilinos
-- pertence ao banco, porque o banco é a única camada que nenhum código futuro dribla por
-- esquecimento.
--
-- Este é o PRIMEIRO trigger do projeto. Foi decisão consciente: até aqui nenhuma regra tinha
-- essa combinação de "silenciosa + vaza dado entre clientes".

CREATE OR REPLACE FUNCTION valida_hierarquia_igreja() RETURNS trigger AS $$
BEGIN
    -- Virar independente (ou continuar) nunca viola nada.
    IF NEW.igreja_mae_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.igreja_mae_id = NEW.id THEN
        RAISE EXCEPTION 'Igreja % nao pode ser mae de si mesma', NEW.id
            USING ERRCODE = 'check_violation';
    END IF;

    -- FOR UPDATE, e não um SELECT simples, é o que faz esta checagem valer sob concorrência.
    -- Em READ COMMITTED, duas transações simultâneas leriam o estado antigo uma da outra e as
    -- DUAS passariam num SELECT comum — formando exatamente o ciclo que queremos impedir.
    -- Travando a linha da mãe, elas serializam. No caso patológico (A→B e B→A ao mesmo tempo)
    -- o Postgres detecta deadlock e aborta uma das duas: falha segura, que é o que queremos.
    -- O caminho normal da aplicação nunca chega aqui em conflito, porque o VinculoService já
    -- trava as duas linhas em ordem de UUID antes de validar.
    PERFORM 1 FROM igreja WHERE id = NEW.igreja_mae_id FOR UPDATE;

    IF EXISTS (
        SELECT 1 FROM igreja mae
        WHERE mae.id = NEW.igreja_mae_id
          AND mae.igreja_mae_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Igreja % ja e congregacao e nao pode ter congregacoes (regra dos 2 niveis)',
            NEW.igreja_mae_id
            USING ERRCODE = 'check_violation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM igreja filha
        WHERE filha.igreja_mae_id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Igreja % tem congregacoes e nao pode virar congregacao (regra dos 2 niveis)',
            NEW.id
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- UPDATE OF igreja_mae_id: o trigger só roda quando essa coluna é tocada. Salvar outros
-- campos da igreja (nome, endereço, logo) não paga o custo da checagem.
CREATE TRIGGER trg_igreja_hierarquia
    BEFORE INSERT OR UPDATE OF igreja_mae_id ON igreja
    FOR EACH ROW
EXECUTE FUNCTION valida_hierarquia_igreja();
