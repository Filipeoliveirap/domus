# Contas a pagar (ledger + lembrete) — design

> **Status**: v2 aprovada no brainstorm (2026-09-20). Back v1 (V41) já existe,
> testado; v2 é edição in-place com 6 deltas aditivos (anexo, filtro/totais,
> recorrência por vezes, dia-âncora, bug > mensal, endpoint de projeção).
> Item 5 do `BACKLOG-PRE-VENDA.md`. Escopo decidido em 2026-08-20: Domus **não**
> executa pagamento — registrar, lembrar e lançar no financeiro. Só isso.

## Problema

O financeiro da igreja só enxerga o que **já aconteceu** (`movimentacao_financeira`,
ENTRADA/SAÍDA). O que está **por acontecer** — aluguel do templo, água, luz,
internet, seguro — não existe no sistema. O tesoureiro mantém isso na cabeça ou em
planilha de fora, e:

1. Atrasa conta porque ninguém lembrou.
2. Quando paga, lança uma SAÍDA manual que não tem relação com nada — o
   balancete é montado depois dos fatos, nunca antes.
3. É item que todo concorrente tem — aparece como "faltando" na comparação
   de features na hora da venda.

## Decisões do brainstorm (2026-09-16)

| # | Decisão |
|---|---|
| 1 | **Acesso**: capacidade `TESOUREIRO` (já existe) + `ADMIN_IGREJA`. Zero modelo novo. |
| 2 | **Recorrência entra na v1** (conta mensal fixa: aluguel, internet...). |
| 3 | **Lembretes**: 7 dias antes, 3 dias antes e no dia do vencimento, in-app (central de notificações). E-mail fora da v1. |
| 4 | **Pagamento parcial na v1**: conta pode ser paga em partes (`valor_pago` acumula; conta fecha quando soma ≥ valor). |
| 5 | **Atraso re-notifica a cada 3 dias** enquanto `EM_ABERTO` e vencida. |
| 6 | **Cancelar conta = soft-delete** (padrão `SQLDelete`/`SQLRestriction` do módulo). Sem status CANCELADA. |
| 7 | **Categoria obrigatória**, filtrando só tipo SAÍDA — coerência com `movimentacao_financeira.categoria_id NOT NULL`. |

### Decisões adicionais (v2, 2026-09-20)

| # | Decisão | Justificativa |
|---|---|---|
| 8 | **Anexo vira entidade própria** (`anexo` tabela, `anexo_id` em `conta_a_pagar` e `pagamento_conta`), reusa `ArmazenamentoFotos` (R2) mas com prefixo `anexos/`. | O módulo `foto/` é específico de imagem otimizada (WebP, display/thumb); anexo é arquivo cru (PDF/JPEG/PNG) com `nome_original` pra download amigável. Mesmo storage genérico, modelagem nova. |
| 9 | **Recorrência aceita data-fim OU nº de ocorrências** (ambos opcionais; o que vier primeiro prevalece). Default: 12 ocorrências. | Casa com mental model do protótipo ("repetir 12 vezes") e mantém a forma anterior ("até 31/12/2026"). |
| 10 | **Bug dia 31**: dia-âncora (1-28) obrigatório quando vencimento ∈ [29,31]; série usa dia-âncora em meses curtos. | Padrão Google Calendar; alinha UX ao calendário do usuário. |
| 11 | **Bug recorrência > mensal**: job busca a última ocorrência materializada da série (não `vencimento + 45 dias`) pra calcular a próxima — cobre TRIMESTRAL/SEMESTRAL/ANUAL. | Janela rolante de 45 dias não cobre 90/180/365. Idempotência preservada via check `existeOcorrenciaFutura`. |
| 12 | **Endpoint de projeção** (`GET .../series/{id}/projecao`): devolve ocorrências materializadas **+ previstas** até o teto (`recorrenciaAte` ou `recorrenciaVezes`). Previstas vêm marcadas `materializada=false` (não existem fisicamente, só projeção). | Mostra "aluguel vence em 14/10, 14/11, 14/12..." sem materializar tudo. |
| 13 | **Filtro por fornecedor (texto, `LIKE %x%`)** na listagem + **endpoint de totais** (`GET .../resumo` → 4 números: vence hoje / a vencer no mês / atrasadas / pagas no mês). | Reduz round-trips do front (KPI cards + tabela numa só chamada). |

## Modelo

### `conta_a_pagar` (V41)

```
id                        UUID PK
igreja_id                 UUID NOT NULL FK igreja          (isolamento multi-tenant)
categoria_id              UUID NOT NULL FK categoria_financeira (validada tipo SAIDA no service)
fornecedor                VARCHAR(120) NOT NULL             (texto livre — sem tabela própria, YAGNI)
descricao                 VARCHAR(255)                      (opcional; "conta de luz de setembro")
valor                     NUMERIC(15,2) NOT NULL CHECK (valor > 0)
vencimento                DATE NOT NULL
status                    VARCHAR(20) NOT NULL              -- EM_ABERTO | PAGA
valor_pago                NUMERIC(15,2) NOT NULL DEFAULT 0  -- soma dos pagamentos parciais
pago_em                    DATE                             -- data do 1º pagamento (movimentações carregam as próprias)
serie_id                  UUID NULL FK conta_a_pagar (self) -- recorrência, ver abaixo
diverge_da_serie          BOOLEAN NOT NULL DEFAULT false
recorrencia_frequencia    VARCHAR(20) NULL                  -- MENSAL | TRIMESTRAL | SEMESTRAL | ANUAL (só na geradora)
recorrencia_ate           DATE NULL                          -- teto temporal; NULL = sem teto
recorrencia_vezes         INT NULL                           -- (v2) teto por ocorrência; NULL = sem teto
recorrencia_dia_ancora    INT NULL CHECK (recorrencia_dia_ancora BETWEEN 1 AND 28) -- (v2) só quando vencimento ∈ [29,31]
anexo_id                  UUID NULL FK anexo                 -- (v2) fatura/boleto PDF ou imagem
criado_por_usuario_id     UUID NULL FK usuario
criado_por_texto          VARCHAR(255)
deleted_at, created_at, updated_at                          (soft-delete padrão)
```

Índices: `(igreja_id, status, vencimento)`, `(igreja_id, serie_id)`,
`(igreja_id, fornecedor)` (v2, pro filtro de listagem).

### Pagamentos parciais: `pagamento_conta` (linha por lançamento)

Cada "pagar X" cria UMA `movimentacao_financeira` (SAÍDA) e UMA linha de rastro:

```
pagamento_conta: id, conta_id FK, movimentacao_id FK,
                 valor_pago (o que saiu de fato), juros_acrescimos, desconto,
                 pago_em, forma (texto: PIX/Boleto/TED/Dinheiro...), criado_por...,
                 anexo_id FK anexo NULL                -- (v2) comprovante do pagamento
```

Regras (atualizado 2026-09-16, 2ª rodada do brainstorm com o protótipo do Stitch):

- Pagar pede `valor > 0`; o valor da movimentação SAÍDA é
  `valor_pago + juros_acrescimos - desconto` (o que saiu do banco de verdade,
  para o balancete bater). `valor_pago` (coluna da conta) acumula só o
  `valor_pago` do título (juros/desconto não amortizam o principal).
- Quando `valor_pago >= valor`, `status = PAGA`.
- **Juros/Acréscimos e Descontos** (vêm do protótipo, entraram na v1): campos
  do modal de pagamento, default 0. Juros de atraso e desconto obtido são
  reais e precisam estar na SAÍDA pra bater com o extrato.
- **Encerrar por abatimento**: se sobrar resto pequeno, o tesoureiro paga o
  restante parcial mesmo e a UI oferece "dar baixa no restante" — gera ajuste
  silencioso: conta marcada PAGA com `valor_pago < valor` e nota. Sem tabelas
  novas; a diferença nunca vira movimentação (não houve dinheiro).
- **Desfazer pagamento (v1 — decisão do usuário 2026-09-16)**: cada linha de
  `pagamento_conta` tem botão "Estornar". Segue o padrão de estorno já usado no
  `MovimentacaoAutomaticaService` para evento: **não apaga** a movimentação —
  gera contra-lançamento (ENTRADA espelhando a SAÍDA, texto "Estorno de
  pagamento — [fornecedor]"), marca a linha como `estornado=true` (data,
  operador) e recalcula `valor_pago`/`status` da conta (volta a EM_ABERTO se
  ficou abaixo do valor). Preserva o rastro contábil de que houve pagamento e
  depois estorno, igual ao estorno de inscrição.

### Campos extras vindos do protótipo (2ª rodada do brainstorm, todos v1)

| Campo | Tipo | Nota |
|---|---|---|
| `competencia` | DATE (nullable) | mês de referência da despesa ("aluguel de novembro"), distinto do vencimento; usado em agrupamento futuro |
| `linha_digitavel` | VARCHAR(80) (nullable) | código de barras/linha digitável de boleto; botão "copiar" no front. Ler-via-câmera fica fora (v2) |
| `documento_numero` | VARCHAR(40) (nullable) | nº do documento/título (ex.: #CPFL-202411-982) |
| `cnpj_fornecedor` | VARCHAR(20) (nullable) | texto livre validado só no front (máscara); **sem** tabela de fornecedor |
| `observacoes` | TEXT (nullable) | notas internas |
| `anexo` | arquivo único (nullable) | **(v2 refinado)** FK pra `anexo` (não arquivo binário inline). Anexo carrega o PDF/JPEG/PNG da fatura. O **comprovante do pagamento** é FK análoga em `pagamento_conta.anexo_id`. Ver seção "Anexos" abaixo. |

Status exibido: além de EM_ABERTO/PAGA, o front mostra badge **"Parcial"** quando
`valor_pago > 0 && valor_pago < valor` — o protótipo acertou esse ponto.

### Recorrência (v1 simples, modelo da Spec C adaptado; refinado em v2)

- `serie_id` aponta pra **a própria conta geradora** (primeira da série);
  `diverge_da_serie` marca conta editada/cancelada individualmente.
- Config no cadastro: `frequencia` (MENSAL | TRIMESTRAL | SEMESTRAL | ANUAL) +
  teto por **data** (`recorrencia_ate`) e/ou **nº de ocorrências** (`recorrencia_vezes`,
  v2). O que vier primeiro prevalece. Default sugerido na UI: 12 ocorrências.
- **Não** materializa série no futuro: o `ContasAPagarJob` diário (cron 06:15,
  junto do padrão do `EventoSerieMaterializacaoJob`) cria a próxima ocorrência
  quando a **última ocorrência materializada** da série estiver a ≤ 45 dias
  do vencimento — janela rolante, igual à Spec C. v2 corrige o caso > mensal
  (ver #11): a busca parte da última ocorrência materializada, não do
  vencimento da geradora, e o teto (data/vezes) limita até onde materializar.
- Dia-âncora (v2, decisão #10): se `vencimento` ∈ {29, 30, 31}, o cadastro exige
  `recorrencia_dia_ancora ∈ [1, 28]` e a série usa esse dia nos meses curtos.
  Ex.: ancorada em 28, 31/01 → 28/02 → 28/03 → 31/03. Sem essa proteção o
  drift mensal já documentado acontece.
- Editar uma ocorrência pergunta o escopo (ESTA | ESTA_E_SEGUINTES | SERIE),
  reusando o modelo mental do `ModalEscopoEdicaoEvento`. Cancelar conta =
  soft-delete; cancelar série = soft-delete em todas as não-divergentes.
- Diferença pro evento: sem exceções ("pular um feriado") — para de pagar =
  cancelar a série ou a ocorrência. YAGNI.

## Lembretes (job diário, `ContasAPagarJob`)

Notificação in-app (`NotificacaoService.criar`, transacional, chamada do próprio
job — não outbox: lembrete não é evento de negócio que precisa de retry-até-a-
entrega) destinada a **TESOUREIRO + ADMIN_IGREJA** da igreja:

| Quando | Condição | Novo `TipoNotificacao` |
|---|---|---|
| 7 dias antes | `vencimento - hoje == 7` | `CONTA_A_PAGAR_VENCENDO` |
| 3 dias antes | `vencimento - hoje == 3` | idem |
| no dia | `vencimento == hoje` | idem |
| atrasada | `vencimento < hoje` && status EM_ABERTO | idem (texto diferente) |

Atrasada re-notifica **a cada 3 dias** (`vencimento` ainda em aberto a N dias
onde `(hoje - vencimento) % 3 == 0`). Job roda 1×/dia às 06:15 — idempotente
por construção (checa o dia, não "já notifiquei").

## API

```
GET    /financeiro/contas-a-pagar?status=&vencimentoAte=&page=   (lista: em aberto/atrasadas/pagas)
POST   /financeiro/contas-a-pagar                                (cadastrar, com recorrência opcional)
GET    /financeiro/contas-a-pagar/{id}
PUT    /financeiro/contas-a-pagar/{id}?escopo=ESTA|ESTA_E_SEGUINTES|SERIE
DELETE /financeiro/contas-a-pagar/{id}?escopo=ESTA|SERIE         (soft-delete)
POST   /financeiro/contas-a-pagar/{id}/pagamentos                (pagar total ou parcial)
POST   /financeiro/contas-a-pagar/{id}/dar-baixa-restante        (fecha com abatimento)
```

Autorização: `@PreAuthorize` com TESOUREIRO/ADMIN + capacidade, mesmo padrão do
restante do financeiro. Atraso é **calculado** na resposta (não coluna) —
`EM_ABERTO` + `vencimento < hoje` → seção "Atrasadas" no front.

### Endpoints novos (v2)

```
GET    /financeiro/contas-a-pagar/resumo?mesReferencia=YYYY-MM-DD      (KPIs: vence hoje/a vencer no mês/atrasadas/pagas no mês)
GET    /financeiro/contas-a-pagar?fornecedor=CPFL                      (filtro adicional)
GET    /financeiro/contas-a-pagar/series/{serieId}/projecao            (ocorrências materializadas + previstas até o teto)
POST   /anexos                                                          (multipart, retorna {id, tipo, bytes, nomeOriginal})
GET    /anexos/{id}                                                     (download com Content-Disposition: nome original)
DELETE /anexos/{id}                                                     (remove arquivo + linha; idempotente)
```

## Pagar → movimentação

`ContaAPagarService.pagar(conta, valor, data)`:

1. Valida `valor > 0` e `valor_pago + valor <= valor`.
2. Cria `MovimentacaoFinanceira` SAÍDA (categoria da conta, valor do
   **pagamento**, não da conta — pagamento parcial lança o valor parcial,
   que é o que saiu do banco), `criado_por` = operador.
3. Cria `pagamento_conta` ligando conta ↔ movimentação.
4. Atualiza `valor_pago` (+= valor) e `status` (PAGA se cobriu).
5. Tudo numa transação só — o mesmo padrão transacional do
   `MovimentacaoAutomaticaService` (que continua sendo o único caminho pra
   entrada/estorno de evento; contas a pagar é o caminho de SAÍDA planejada).

## Anexos (v2)

PDF/JPEG/PNG de faturas/boletos e comprovantes de pagamento. Mesmo bucket R2,
**prefixo `anexos/`** (não `fotos/`) e **mesmo `ArmazenamentoFotos`** (interface
genérica do `shared/armazenamento`) — zero infra nova, só modelagem nova.

### Entidade `anexo` (V44)

```
id              UUID PK
igreja_id       UUID NOT NULL FK igreja          (isolamento multi-tenant; nunca cruza)
chave           VARCHAR(255) NOT NULL UNIQUE     -- "anexos/{igrejaId}/{uuid}"
tipo            VARCHAR(50) NOT NULL              -- MIME: application/pdf | image/jpeg | image/png
bytes           BIGINT NOT NULL CHECK (bytes > 0) -- tamanho em bytes
nome_original   VARCHAR(255) NOT NULL             -- preserva pra download amigável
created_at      TIMESTAMP
```

Validações no upload (no `AnexoService`, antes de tocar storage):
- `Content-Type` ∈ {`application/pdf`, `image/jpeg`, `image/png`}.
- Tamanho ≤ 10 MB (após descobrir que o `FotoService` não tem limite, decidimos
  aplicar — anexo é maior que foto).
- `nome_original` sanitizado (sem `..`, sem `/`, ≤ 255 chars).
- Igreja do JWT é gravada no anexo; chave inclui `igrejaId`.

### Vínculo com conta e pagamento

- `conta_a_pagar.anexo_id` (nullable, FK `anexo.id`) — fatura/boleto.
- `pagamento_conta.anexo_id` (nullable, FK `anexo.id`) — comprovante do pagamento.
- Upload é em duas etapas: primeiro o front chama `POST /anexos` com o arquivo,
  recebe `{id}`, e usa esse `id` no `POST /financeiro/contas-a-pagar` ou
  `POST /.../pagamentos`. Vantagem: retry de upload independente do envio da
  conta, e o back não precisa entender multipart misturado com JSON.

### Soft-delete de anexo

- `DELETE /anexos/{id}` remove do bucket e do banco na mesma transação.
- A remoção do bucket é agendada pra depois do commit (mesmo padrão do
  `FotoService.remover` — bucket não participa da transação).
- Anexos órfãos (sem FK referenciando após exclusão) são limpos por job
  semanal (decisão adiada — pode aproveitar o `LimpezaFotosJob` se o critério
  bater, ou criar `LimpezaAnexosJob` próprio).

## Front

- Tela `/financeiro/contas-a-pagar`: mesmas réguas visuais de
  `/financeiro/movimentacoes` — lista com abas **Em aberto / Atrasadas /
  Pagas**, card com fornecedor, valor, vencimento, badge de dias restantes/
  atrasadas, categoria com cor (reusa padrão cor-por-categoria existente).
- Ações: cadastrar (modal), editar, excluir (confirm com escopo se série),
  **Pagar** (modal: valor default = restante, campo data default hoje).
- Barra de resumo: total em aberto, total atrasado, vence nos próximos 7 dias.
- Recorrência no cadastro: toggle "repetir" + frequência — aparece só quando
  relevante, sem jargão.
- Notificação cai no sino existente; texto tipo "Conta de luz vence em 3 dias
  (R$ 182,40)".

## Fora do escopo (de propósito)

- Executar pagamento de verdade (item `BACKLOG-MELHORIAS-FUTURAS.md`).
- Contas a **receber** (dízimo/ofertosa são `movimentacao` + evento pago; fluxo
  a receber dedicado é outro item).
- E-mail de lembrete (v1 in-app; tesoureiro abre o app com frequência).

## Mudanças no `ContaAPagarService` (v2)

1. **`criar`**: aceita `recorrenciaVezes` e `recorrenciaDiaAncora` no `ContaRequest`.
   Validação: se `vencimento.getDayOfMonth() ∈ [29, 31]` E `frequencia != null`,
   `recorrenciaDiaAncora` é obrigatório e ∈ [1, 28]. Cross-field: o DTO usa
   `@AssertTrue` (mesmo padrão dos outros módulos) pra mensagem clara.
2. **`materializarRecorrencias`**: corrigido o bug > mensal. Em vez de
   `findGeradorasParaMaterializar` retornar geradoras com `vencimento <= hoje+45d`
   e materializar 1 ocorrência a partir do vencimento da geradora, retorna a
   **última ocorrência materializada** da série e calcula a próxima a partir
   dela (`+ 1 período`). Limita por `recorrenciaAte` e `recorrenciaVezes`. Sem
   filtro de status na geradora (preservado).
3. **`projetarSerie(UUID serieId)`**: novo método. Lê a série + todas as
   ocorrências materializadas (`deleted_at IS NULL`); projeta ocorrências
   futuras até o teto (data OU vezes) e devolve uma lista com flag
   `materializada: boolean` em cada item. **Sem efeito colateral**.
4. **`resumo(UUID igrejaId, YearMonth mes)`**: novo método, alimenta
   `GET /financeiro/contas-a-pagar/resumo`. 4 contagens agregadas em SQL
   nativo (não JPQL) pra ficar simples e barato.
5. **`listar`** ganha `fornecedor: String` opcional (LIKE `%x%`).
6. **Diverge-da-série no escopo**: ao editar `escopo=ESTA_E_SEGUINTES` ou
   `SERIE`, todas as ocorrências alvo passam a `diverge_da_serie=true` (exceto
   a geradora). Necessário pro job de recorrência saber que não deve
   reescrever essas.

## Mudanças no `ContasAPagarJob` (v2)

- Materialização lê do `materializarRecorrencias` corrigido. Janela rolante
  preservada (a última ocorrência materializada que estiver a ≤ 45 dias do
  vencimento aciona a próxima).
- Lembretes: inalterados.

## Migration V44 (v2)

```sql
-- 1. Entidade anexo
CREATE TABLE anexo (
    id              UUID PRIMARY KEY,
    igreja_id       UUID NOT NULL REFERENCES igreja(id),
    chave           VARCHAR(255) NOT NULL UNIQUE,
    tipo            VARCHAR(50) NOT NULL,
    bytes           BIGINT NOT NULL CHECK (bytes > 0),
    nome_original   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_anexo_igreja ON anexo(igreja_id);

-- 2. Vínculo na conta
ALTER TABLE conta_a_pagar
    ADD COLUMN anexo_id UUID NULL REFERENCES anexo(id),
    ADD COLUMN recorrencia_vezes INT NULL,
    ADD COLUMN recorrencia_dia_ancora INT NULL CHECK (recorrencia_dia_ancora BETWEEN 1 AND 28);
CREATE INDEX idx_conta_a_pagar_fornecedor ON conta_a_pagar(igreja_id, fornecedor);

-- 3. Vínculo no pagamento
ALTER TABLE pagamento_conta
    ADD COLUMN anexo_id UUID NULL REFERENCES anexo(id);
```

**Sem `down`**: a v41 (que existe em prod) usa V01-V40; a v44 é aditiva e não
quebra nada. O `down` é opcional e listado em comentário no topo do arquivo.

## Critérios de aceitação (v2)

1. Upload de PDF de boleto anexa à conta e mostra preview (ícone de PDF) na
   listagem. PDF de 11 MB retorna 400 com mensagem clara.
2. Recorrência com vencimento em dia 31 e frequência MENSAL cadastrada hoje
   gera ocorrências em 28/02 e 31/03 do ano que vem (sem drift).
3. Recorrência com 5 ocorrências restantes materializa só 1 a 5 ocorrências
   conforme o teto, e `GET /projecao` mostra as 5 com `materializada=false` nas
   que ainda não existem.
4. `GET /resumo?mesReferencia=2024-11` retorna:
   ```json
   {"venceHoje": 1850.00, "aVencerNoMes": 14320.50, "atrasadas": 850.00, "pagasNoMes": 28940.00}
   ```
5. Estornar um pagamento que tinha comprovante anexo: o comprovante **permanece**
   vinculado ao `pagamento_conta` (estorno não apaga histórico; mesmo padrão da
   movimentação).
6. Filtro `?fornecedor=CPFL` retorna só contas com fornecedor contendo "CPFL"
   (case-insensitive).
7. `ContaAPagarServiceTest` cobre: dia-âncora inválido recusa; recorrência
   trimestral gera próxima ocorrência a partir da última materializada;
   `projetarSerie` para no teto e marca `materializada=false` corretamente;
   `resumo` conta status corretamente em mudança de mês.

## Do protótipo do Stitch: adotado vs. adiado (2026-09-16)

**Adotado na v1** (já incorporado acima): desfazer pagamento, juros/acréscimos e
descontos no pagamento, competência, linha digitável com copiar, nº de documento,
CNPJ do fornecedor (texto), anexo (1 arquivo por conta + comprovante por
pagamento), observações, badge "Parcial", "Salvar e já dar baixa".

**Adotado na v2** (recorte do brainstorm de 2026-09-20): anexo virou entidade
própria com FK; filtro por fornecedor na listagem; endpoint de totais; KPI
"vence hoje" / "a vencer no mês"; recorrência por nº de ocorrências; dia-âncora
pra vencimento 29-31; endpoint de projeção da série (mostra calendário do
protótipo sem materializar tudo).

**Adiado** (anotado no `BACKLOG-MELHORIAS-FUTURAS.md`, não entra agora):
tabela de fornecedores, contas bancárias (multi-banco/alçada), centro de custo
(`movimentacao_financeira` não tem a coluna — seria migration + modelagem própria),
ler código de barras via câmera, alçadas de aprovação (validação conjunta acima
de valor X), calendário visual de vencimentos (UI pode desenhar com o endpoint
de projeção; backend não precisa mudar), exportar PDF/XLSX, baixa em lote,
dashboard de distribuição por centro de custo, saldo/disponibilidade de caixa,
histórico de auditoria dedicado (o log genérico já preserva o rastro de
pagamentos/estornos).