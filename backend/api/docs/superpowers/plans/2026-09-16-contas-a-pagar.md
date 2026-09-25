# Contas a pagar — plano de implementação

> **Status do plano:** Task 1 (schema) **FEITA** e em revisão (dev-database).
> Spec atualizada com decisão #14 (`beneficiario` XOR pessoa/texto livre — ver
> `docs/superpowers/specs/2026-09-16-contas-a-pagar-design.md`). Próximos:
> Task 2 (backend) é o gargalo; Task 3 (testes) e Task 4 (frontend) rodam em
> paralelo após a Task 2 com revisão OK.

## Dependências entre tasks

```
Task 1 (schema)         ← FEITA, aguardando revisão do dev-database
   ↓
Task 2 (backend: entities + service + controller + job + DTOs)
   ↓ (libera os dois em paralelo quando Task 2 com revisão OK)
   ├── Task 3 (testes do service)         ← Mockito puro, sem Spring
   └── Task 4 (frontend: página + modais) ← depende dos endpoints HTTP
```

**Por que Task 3 e 4 em paralelo:** Task 3 valida regra de negócio isolada
(Mockito puro) sem precisar de UI nem HTTP. Task 4 depende só do contrato HTTP
(URL, método, payload, response) — não roda `mvn test` nem importa código Java.
Os dois não se tocam em disco. Se a Task 4 descobrir inconsistência no contrato,
isso cai na revisão da Task 2 e o agent da Task 3 ajusta os mocks — não bloqueia.

## Os 4 passos

### 1. Schema ✅ FEITO, em revisão
- **V41**: `conta_a_pagar` (com `beneficiario_pessoa_id` XOR `beneficiario_texto`),
  `pagamento_conta`, índices, CHECKs.
- **V45**: entidade `anexo` + FKs `conta_a_pagar.anexo_id` e `pagamento_conta.anexo_id`.
- Ver `.superpowers/sdd/2026-09-16-contas-a-pagar/task-1-brief.md` e
  `task-1-review-pkg.md` (resultado da revisão).

### 2. Backend (próxima)
Módulo `financeiro/contapagar`. Implementa:

- **Entities JPA**: `ContaAPagar`, `PagamentoConta`, `Anexo` (padrão do projeto:
  `@SQLDelete`/`@SQLRestriction`, builder, `BaseEntity` herdado de onde?)
- **Enums**: `StatusContaAPagar` (`EM_ABERTO`, `PAGA`); `FormaPagamento`
  (`PIX`, `BOLETO`, `CARTAO_CREDITO`, `CARTAO_DEBITO`, `DINHEIRO`,
  `TRANSFERENCIA`, `OUTRO`); `RecorrenciaFrequencia` (`MENSAL`, `TRIMESTRAL`,
  `SEMESTRAL`, `ANUAL`); `EscopoEdicaoSerie` (`ESTA`, `ESTA_E_SEGUINTES`,
  `SERIE`).
- **Repositories**: `ContaAPagarRepository` (com queries por igreja+status,
  igreja+vencimento, listagem de série, projeção); `PagamentoContaRepository`;
  `AnexoRepository`.
- **DTOs** (request + response): criar/editar/listar/detalhe conta, registrar
  pagamento, resumo (4 KPIs), projeção série.
- **`AnexoService`**: upload (validação MIME, tamanho ≤ 10MB, sanitização de
  nome, igreja do JWT), download (com `Content-Disposition: nome_original`),
  delete (bucket + DB em transação com remoção assíncrona confirmada — ver
  padrão do `FotoService.remover`).
- **`ContaAPagarService`** (módulo service):
  - `criar(request)` — valida categoria tipo SAÍDA, monta `beneficiario`
    (FK XOR texto), valida dia-âncora quando vencimento ∈ [29,31], persiste;
    se recorrência, configura o objeto modelo (`serie_id = null`,
    `recorrencia_frequencia` setada).
  - `editar(id, escopo, request)` — atualiza conforme escopo (modelo do
    `ModalEscopoEdicaoEvento`); marca `diverge_da_serie` quando escopo = ESTA
    ou ESTA_E_SEGUINTES.
  - `excluir(id, escopo)` — soft-delete conforme escopo.
  - `pagar(id, request)` — cria `MovimentacaoFinanceira` SAÍDA com valor
    líquido (`valor_pago + juros - desconto`), vincula `pagamento_conta`,
    atualiza `valor_pago` e status (PAGA se cobriu), tudo numa transação.
    Padrão análogo ao `MovimentacaoAutomaticaService` (mas focado em SAÍDA).
  - `estornarPagamento(id)` — soft-estorno: `estornado=true`, contra-lançamento
    ENTRADA (texto "Estorno de pagamento — [beneficiário]"), recalcula
    `valor_pago`/`status` na conta (volta a EM_ABERTO se ficou abaixo).
  - `darBaixaRestante(id)` — `status=PAGA` sem movimentação; comentário interno
    dizendo "baixa por abatimento". Sem tabela nova.
  - `listar(filtros)` — paginado, com filtros `status`, `vencimentoAte`,
    `beneficiarioTexto` (case-insensitive).
  - `resumo(mesReferencia)` — 4 contagens agregadas em SQL nativo (não JPQL)
    pra ficar simples e barato.
  - `projetarSerie(serieId)` — ocorrências materializadas + previstas até o teto
    (`recorrencia_ate` OU `recorrencia_vezes`), flag `materializada` por item.
  - `materializarRecorrencias()` — **corrigido** (decisão v2 #11): busca a
    **última ocorrência materializada** da série (não `vencimento + 45d`); calcula
    próxima a partir dela (`+ 1 período`); limita por `recorrencia_ate` e
    `recorrencia_vezes`. Idempotência via `existeOcorrenciaFutura`.
  - `desvincularBeneficiario(UUID pessoaId)` — chamado por
    `PessoaService.excluirDefinitivo`. Converte todas as contas com
    `beneficiario_pessoa_id = pessoaId` em texto "Pessoa removida do sistema"
    num batch único. Mesma filosofia do
    `EventoResponsavelRepository.desvincularPessoa`.
- **`ContaAPagarController`**: endpoints REST (ver spec "API"). `PreAuthorize`
  TESOUREIRO/ADMIN igual ao restante do financeiro.
- **Job** `ContasAPagarJob` (`@Scheduled(cron = "0 15 6 * * *")` — copiado do
  padrão `EventoSerieMaterializacaoJob`):
  - Chama `ContaAPagarService.materializarRecorrencias()`.
  - Lembrete 7 dias antes: cria `Notificacao` `CONTA_A_PAGAR_VENCENDO` pra
    TESOUREIRO + ADMIN_IGREJA. Idempotente por dia (job checa o dia, não "já
    notifiquei").
  - Lembrete 3 dias antes, no dia, e re-notificação a cada 3 dias de atraso:
    mesma notificação, texto diferente.
- `AnexoController`: `POST /anexos` (multipart), `GET /anexos/{id}` (download),
  `DELETE /anexos/{id}` (remove bucket + DB).

Detalhes completos em `.superpowers/sdd/2026-09-16-contas-a-pagar/task-2-brief.md`.

### 3. Testes do service (paralela à Task 4)

`ContaAPagarServiceTest` cobrindo 16 cenários (ver `task-3-brief.md`):
- `MockitoExtension`, mocks manuais no `@BeforeEach` (estilo A do projeto);
- Categoria SAÍDA válida vs ENTRADA recusa;
- Dia-âncora obrigatório quando vencimento ∈ [29-31];
- Pagamento parcial (status EM_ABERTO, badge Parcial);
- Pagamento total (status PAGA);
- Juros/desconto na movimentação (líquido);
- Estorno recalcula e gera contra-lançamento;
- Dar baixa restante sem movimentação;
- Materialização mensal a partir da última ocorrência materializada;
- `materializar` respeita teto (`recorrencia_vezes`);
- `projetarSerie` marca corretamente `materializada=false`;
- Edição por escopo marca `diverge_da_serie` corretamente (ESTA, ESTA_E_SEGUINTES);
- (e desvinculação de beneficiário quando `PessoaService` chamar)

Detalhes em `.superpowers/sdd/2026-09-16-contas-a-pagar/task-3-brief.md`.

### 4. Frontend (paralela à Task 3)

Página `/financeiro/contas-a-pagar` com:
- 4 cards KPI no topo (vence hoje / a vencer no mês / atrasadas / pagas no mês);
- Abas Em aberto / Atrasadas / Pagas (+ badge Parcial);
- Filtros: status, vencimento até, **beneficiário** (texto livre — só busca
  entre os com `beneficiario_texto` preenchido);
- Lista em tabela desktop / cards mobile;
- Modal de cadastro / edição (reusando `<BeneficiarioField>` componente novo);
- Modal de pagamento (valor, juros, desconto, data, forma, comprovante — upload via `<UploadAnexo>`);
- Modal de escopo de edição/exclusão para séries;
- Botão "Salvar e já dar baixa" no form de cadastro.

Detalhes em `.superpowers/sdd/2026-09-16-contas-a-pagar/task-4-brief.md`.

## Reviewer por task

| Task | Reviewer primário | Especialista |
|---|---|---|
| 1 | dev-database | (nenhum — schema é o entregável) |
| 2 | code-reviewer + dev-back | gap-correctness + gap-perf |
| 3 | test-writer (auto-revisão) | gap-testing |
| 4 | code-reviewer + dev-front | ux-reviewer (tela é a entrega) |

## Ordem e checkpoints

1. ✅ Task 1: schema + review do dev-database
2. ⏳ Task 2 (próxima): backend — bloqueia Tasks 3 e 4
3. ⏳ Tasks 3 e 4 em paralelo: liberados quando Task 2 OK
4. 🔄 Cada task termina com `mvn -q test` (back) ou `npm test` (front) verde antes de declarar pronta
5. 🔄 Merge sequencial por task após revisão OK

## Critério de "pronto" do plano

- Cada task revisada e aprovada pelo seu reviewer
- Suíte verde local por task antes de declarar pronta (1077+ testes no repo,
  Tasks 2-4 não podem regredir)
- Commit único por task após review OK
- Merge só depois de todas as tasks (Task 2 pode mergear sozinha; 3 e 4 entram em PRs/commits separados)
