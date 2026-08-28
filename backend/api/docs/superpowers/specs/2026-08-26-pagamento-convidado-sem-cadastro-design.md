# Pagamento para convidado sem cadastro (Plano 4b) — design

> Continuação da spec `2026-08-26-fluxo-pagamento-evento-ux-design.md`, que deixou
> explicitamente fora de escopo o pagamento para Visitante/Pessoa de fora e a
> "unificação acompanhante↔convidado". Brainstorm em 2026-08-26.

## Contexto

O fluxo de pagamento de evento (Planos 1-5 da spec anterior) já cobre pessoa cadastrada
na igreja, em três caminhos: auto-inscrição, lote do gestor ("Pessoas da igreja"), e
convite público pra quem já tem conta. **Convidado sem cadastro** (o que as abas
"Visitantes" e "Pessoa de fora" criam, e o que `/convite/{token}` cria pra quem não tem
conta) nunca teve suporte a cobrança: `InscricaoService.inscreverConvidado` não cria
`CobrancaEvento`, e o schema de `cobranca_evento` nem permite — o `CHECK`
`cobranca_evento_pessoa_xor_acompanhante` exige `pessoa_id` OU `acompanhante_id`, e
convidado sem cadastro não tem nenhum dos dois (é uma `InscricaoEvento` de topo, com
`nomeConvidado`/`telefoneConvidado` direto nela).

No caminho, descobrimos que existe um **segundo** modelo de "gente sem cadastro":
`AcompanhanteInscricao`, mais antigo, aninhado dentro de uma inscrição (usado por quem
já tinha conta e trazia acompanhantes). A investigação mostrou que **a criação de
acompanhante está morta** — `ModalConvidado`/`ModalConfirmarPagamento`/
`useAdicionarConvidado` não são chamados por nenhuma tela viva do app. A leitura
continua em uso (23 linhas históricas, listagem de inscritos, marcação de presença).

## Decisões do brainstorm

- **Não migrar as 23 linhas antigas de `acompanhante_inscricao`.** Ficam como estão,
  histórico puro — só a criação (caminho morto) é removida. Backend (entidade,
  repositório, endpoint, leitura) fica intacto.
- Schema muda pouco: relaxar um `CHECK` e uma coluna `NOT NULL`, não criar tabela nova
  nem migrar dado.

## Seção 1 — Limpeza do caminho morto (frontend)

Remover (nenhum outro arquivo referencia estes três, confirmado por grep antes desta
spec):
- `frontend/src/components/module/eventos/ModalConvidado.tsx`
- `frontend/src/components/module/eventos/ModalConfirmarPagamento.tsx`
- `frontend/src/hooks/inscricao/useAdicionarConvidado.ts`

Não mexer no backend (`AcompanhanteInscricao`, `AcompanhanteRepository`,
`POST /eventos/{eventoId}/inscricoes/{inscricaoId}/acompanhantes`,
`AcompanhanteResponse`, marcação de presença) — continua servindo leitura/histórico.

## Seção 2 — Migration: cobrança para convidado sem cadastro

Nova migration (`V30__cobranca_convidado_sem_cadastro.sql`):

```sql
ALTER TABLE cobranca_evento DROP CONSTRAINT cobranca_evento_pessoa_xor_acompanhante;
ALTER TABLE cobranca_evento ADD CONSTRAINT cobranca_evento_pessoa_xor_acompanhante CHECK (
    (pessoa_id IS NOT NULL AND acompanhante_id IS NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NOT NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NULL)
);

ALTER TABLE cobranca_evento ALTER COLUMN criado_por_usuario_id DROP NOT NULL;
```

- Terceiro caso do `CHECK` (os dois nulos) = cobrança de convidado sem cadastro,
  resolvida só por `inscricao_id` (já `NOT NULL`, já aponta pra uma `InscricaoEvento`
  com `nomeConvidado`/`telefoneConvidado`).
- `criado_por_usuario_id` nulo = auto-registro anônimo via `/convite/{token}` (sem
  sessão, sem usuário nenhum) — mesmo padrão semântico que `inscrito_por_usuario_id`
  já usa pra auto-inscrição (`NULL` = a própria pessoa, não um gestor).

## Seção 3 — Backend: `inscreverConvidado` ganha pagamento

**`CobrancaEventoService`** ganha `criarParaConvidado(igrejaId, eventoId, inscricaoId,
valor, criadoPorUsuarioIdOuNull, gerarLink)` — mesma forma de `criarParaTerceiro`, mas
sem `pessoaId`/`acompanhanteId` (os dois nulos na `CobrancaEvento` criada).

**`InscricaoService.inscreverConvidado`** (hoje só cria a `InscricaoEvento` e retorna)
passa a espelhar `inscreverInterno`:
- Se `evento.getPreco() != null`: valida conta MP conectada (mesma
  `validarContaPagamentoConectada` já existente), cria a inscrição como
  `AGUARDANDO_PAGAMENTO` (hoje sempre `CONFIRMADA`), cria a `CobrancaEvento` via
  `criarParaConvidado`.
- Precisa de um parâmetro novo pra decidir "pagar agora" vs "enviar link" — o
  chamador (controller) já sabe disso pelo contexto: `/convite/{token}` (self-service)
  é sempre "pagar agora" (a própria pessoa está ali); o lote do gestor (Seção 4) manda
  a escolha explícita, igual ao que já existe pra `inscreverPessoas`.
- Retorno do método (hoje só `InscricaoEvento`) precisa carregar também a
  `CobrancaEvento` criada (mesmo padrão de `ResultadoInscricao` que `inscreverInterno`
  já usa) — o controller usa isso pra montar a resposta com `cobrancaId`/`tokenLinkPublico`.

**`CobrancaController`** (`buscarPorId`, `buscar`) e **`MercadoPagoWebhookService`**:
quando `pessoaId` e `acompanhanteId` são os dois nulos, resolver `nomePagador` a partir
de `InscricaoEvento.nomeConvidado` (busca por `inscricaoId`) em vez de
`Pessoa`/`AcompanhanteInscricao`. No webhook, notificar `criadoPorUsuarioId` só quando
não for nulo (auto-registro anônimo não tem ninguém pra notificar).

## Seção 4 — Frontend: abas "Visitantes"/"Pessoa de fora" (`ModalInscreverAlguem`)

Mesmo padrão do Plano 4 (`ModalInscreverPessoas`, "Pessoas da igreja"): depois de
preencher nome/telefone/campos e clicar "Inscrever", se o evento é pago, mostra a
escolha "Pagar inscrição de {nome}" / "Enviar link pra {nome} pagar" (chamando
`useCriarConvidado`, que passa a devolver `cobrancaId`/`tokenLinkPublico`/`gerarLink`
como parâmetro) — em vez de fechar direto como hoje. "Pagar agora" navega pra rota de
checkout (Plano 2); "Enviar link" abre `ModalCompartilharCobranca`, volta pra aba ao
fechar.

## Seção 5 — Frontend: convite público desbloqueia o caminho sem conta

Reverte o bloqueio do Plano 5 (Task 2) — `/convite/{token}` volta a mostrar "Continuar
sem conta" em evento pago. `FormularioConvidado`, ao confirmar (`useEntrarComoConvidado`
→ `criarConvidado`), passa a receber `cobrancaId` na resposta; se presente, navega pra
`/eventos/{eventoId}/pagamento/{cobrancaId}` em vez de chamar `onSucesso()` (tela
estática "Inscrição confirmada!").

## Fora de escopo

- Migrar as 23 linhas antigas de `acompanhante_inscricao` pro modelo convidado (decisão
  do brainstorm: ficam como histórico).
- Apagar a tabela/entidade `AcompanhanteInscricao` — leitura/histórico continuam vivos.
- Qualquer mudança em `adicionarAcompanhante`/marcação de presença de acompanhante
  existente.
