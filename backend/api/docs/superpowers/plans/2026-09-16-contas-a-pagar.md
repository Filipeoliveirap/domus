# Contas a pagar — plano de implementação

> Spec: `docs/superpowers/specs/2026-09-16-contas-a-pagar-design.md` (2026-09-16).
> Migration: **V41__contas_a_pagar.sql**.

## Passos

### 1. Schema (V41)
- Tabela `conta_a_pagar` (ver spec: igreja, categoria SAÍDA obrigatória, fornecedor, competência, linha_digitavel, documento_numero, cnpj_fornecedor, valor, vencimento, status, valor_pago, serie_id self-FK, diverge_da_serie, observacoes, criado_por, soft-delete).
- Tabela `pagamento_conta` (conta FK, movimentacao FK, valor_pago, juros_acrescimos, desconto, forma, estornado boolean + estornado_em/por, soft-delete).
- Índices `(igreja_id, status, vencimento)` e `(igreja_id, serie_id)`.

### 2. Backend — módulo `financeiro/contapagar`
- `ContaAPagar` + `PagamentoConta` (entidades padrão do módulo: `@SQLDelete`/`@SQLRestriction`, builder).
- `StatusConta`: `EM_ABERTO`, `PAGA` (atraso calculado, não coluna).
- `ContaAPagarRepository` com queries de listagem por status/competência/vencimento.
- `ContaAPagarService`:
  - `criar` (valida categoria tipo SAÍDA; recorrência cria série — primeira conta é o gerador).
  - `editar(id, escopo)` / `excluir(id, escopo)` com `ESTA|ESTA_E_SEGUINTES|SERIE` (modelo do `EventoSerie`).
  - `pagar(id, valorPago, juros, desconto, forma, data)` → cria `MovimentacaoFinanceira` SAÍDA (valor líquido) + `pagamento_conta` na mesma transação; atualiza `valor_pago`/`status`.
  - `estornarPagamento(pagamentoId)` → contra-lançamento ENTRADA (padrão `MovimentacaoAutomaticaService`), marca estornado, recalcula.
  - `darBaixaRestante(id)` → fecha por abatimento (sem movimentação).
  - `materializarRecorrencias()` — janela rolante 45 dias (chamada do job).
- `ContasAPagarJob` (`@Scheduled(cron = "0 15 6 * * *")`): materializa recorrências + lembretes (7d, 3d, dia, atrasada a cada 3d) via `NotificacaoService.criar` pra TESOUREIRO+ADMIN.
- `ContaAPagarController` (endpoints da spec) + DTOs; autorização TESOUREIRO/ADMIN igual ao resto do financeiro.

### 3. Testes (backend)
- `ContaAPagarServiceTest`: criar (categoria SAÍDA valida), pagar parcial → Parcial, pagar total → PAGA, estorno recalcula e gera contra-lançamento, juros/desconto na movimentação, dar baixa no restante, recorrência materializa próxima, lembretes 7/3/0/atraso-3d.

### 4. Frontend
- Rota `/financeiro/contas-a-pagar` com abas Em aberto/Atrasadas/Pagas (+ badge Parcial), resumo no topo (vence hoje / a vencer no mês / atrasadas / pagas no mês).
- Modal de cadastro (dados principais, classificação, recorrência, linha digitável, anexo, observações) + "Salvar e já dar baixa".
- Modal de pagamento (valor, juros, desconto, data, forma, comprovante) com total debitado calculado.
- Ações: editar (com escopo se série), excluir (com escopo), estornar pagamento, dar baixa no restante.

## Ordem
1 → 2 → 3 (suíte verde) → 4 → validação manual.