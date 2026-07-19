-- Quatro blocos na tabela igreja, TODOS nuláveis — nenhum quebra o cadastro que já existe
-- nem obriga a preencher nada no fluxo atual. A pessoa completa depois em Configurações,
-- e o que faltar alimenta a barra de completude do cadastro.
--
-- 1. ENDEREÇO — estrutura idêntica à da V11 (membro), de propósito: mesma forma, mesmo
--    componente de front, mesmo auto-preenchimento por CEP.
-- 2. IDENTIDADE/FISCAL — logo, razão social (par do CNPJ, para nota fiscal no futuro)
--    e denominação.
-- 3. AUDITORIA — reusa o padrão de movimentacao_financeira. Junto com o updated_at que
--    já existe, alimenta o card "Logs de atividade".
-- 4. METADADOS DE VÍNCULO — quando/quem vinculou e quando o código foi gerado.
ALTER TABLE igreja
  -- 1. Endereço
  ADD COLUMN cep         VARCHAR(9),
  ADD COLUMN logradouro  VARCHAR(255),
  ADD COLUMN numero      VARCHAR(20),
  ADD COLUMN complemento VARCHAR(255),
  ADD COLUMN bairro      VARCHAR(255),
  ADD COLUMN cidade      VARCHAR(255),
  ADD COLUMN uf          CHAR(2),

  -- 2. Identidade e fiscal
  ADD COLUMN logo_url     VARCHAR(500),
  ADD COLUMN razao_social VARCHAR(255),
  ADD COLUMN denominacao  VARCHAR(255),

  -- 3. Auditoria (quem alterou por último; o quando é o updated_at existente)
  ADD COLUMN atualizado_por_usuario_id UUID REFERENCES usuario (id),

  -- 4. Metadados do vínculo
  -- Quando esta congregação entrou na família. NULL para quem não é congregação.
  ADD COLUMN vinculado_em TIMESTAMP,
  -- Qual admin da congregação digitou o código. O vínculo expõe o financeiro dela,
  -- então "quem autorizou" é auditoria de verdade, não enfeite.
  ADD COLUMN vinculado_por_usuario_id UUID REFERENCES usuario (id),
  -- O código não expira (decisão do design); esta data é o que permite a tela dizer
  -- "gerado há 8 meses" e sugerir rotação.
  ADD COLUMN codigo_gerado_em TIMESTAMP;
