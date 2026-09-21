# Excluir conta (igreja) — design

> Fase 3 do roadmap: "Excluir conta". Aqui "conta" é a **igreja inteira** (o tenant), não
> a conta de um usuário individual — isso já existe (`UsuarioService.excluirDefinitivo`).

## Contexto

Hoje não existe nenhum jeito de uma igreja deixar de existir no sistema. O admin pode
arquivar/excluir pessoas, eventos, células etc. um por um, mas não a igreja como um todo.
Isso é necessário pro caso de uma igreja querer sair da plataforma.

É a ação mais destrutiva e com maior raio de alcance do sistema — toca praticamente todo
módulo (pessoas, eventos, financeiro, células, ministérios, usuários, fotos no R2, índice
do Elasticsearch) e ainda esbarra na relação sede↔congregações (`igreja_mae_id`).

## Decisões já tomadas (durante o brainstorm)

- **Prazo de carência de 10 dias, cancelável**, em vez de exclusão instantânea — reaproveita
  o padrão de soft-delete já usado no resto do sistema, mas aplicado à igreja como um todo.
- **Só `ADMIN_IGREJA` pode agendar ou cancelar.**
- **Reautenticação obrigatória** no momento de agendar: senha (bcrypt) pra quem loga nativo,
  ou um novo ID token do Google (conferido contra o `google_sub` já cadastrado) pra quem só
  tem login Google. Cancelar **não** exige reautenticação — desfazer não é perigoso.
- **Digitar o nome exato da igreja** pra confirmar, mesmo padrão de todo
  `ModalConfirmacaoCritica` já usado no sistema.
- **Durante os 10 dias, a igreja funciona 100% normal** — nenhuma restrição de leitura ou
  escrita. Só um banner fixo pro `ADMIN_IGREJA` com contagem regressiva e botão de cancelar.
  Líder e acesso comum não veem nada diferente.
- **E-mails** pro contato da igreja: ao agendar, 5 dias antes do prazo, 1 dia antes, e quando
  a exclusão realmente acontece.
- **Se a igreja é mãe** (tem congregações vinculadas): elas **não são apagadas** — só saem da
  família e continuam funcionando normalmente, de forma independente. Esse desvínculo só
  acontece **na exclusão definitiva** (depois dos 10 dias), nunca ao agendar — assim,
  cancelar dentro do prazo desfaz 100% sem exceção, família incluída.
- **Nenhuma FK ganha `ON DELETE CASCADE`.** Cogitamos isso pra simplificar a purga, mas
  decidimos que não — hoje um `DELETE FROM igreja` acidental (bug, migration errada, alguém
  mexendo direto no banco) falha alto e claro porque as tabelas são `RESTRICT` por padrão.
  Isso é a última linha de defesa contra apagar uma igreja sem querer, e `CASCADE` removeria
  essa proteção. A purga continua sendo feita **tabela por tabela, explicitamente, na ordem
  certa**, no código da aplicação — mesma filosofia usada o resto do projeto (nunca confiar
  só no banco pra decidir o que apaga o quê).
- **Fora do escopo desta versão:** excluir a família inteira de uma vez (todas as igrejas
  vinculadas juntas). Fica pra quando o pagamento/planos existirem.

## Modelo de dados

Nova migration adicionando em `igreja`:

```sql
ALTER TABLE igreja
  ADD COLUMN exclusao_agendada_em TIMESTAMP,
  ADD COLUMN exclusao_agendada_por_usuario_id UUID REFERENCES usuario(id);
```

`exclusao_agendada_em IS NULL` = sem exclusão agendada. A exclusão definitiva acontece
quando `exclusao_agendada_em + INTERVAL '10 days' <= NOW()`. Cancelar é só zerar as duas
colunas — nenhuma outra tabela é tocada até o prazo vencer de verdade.

Nenhuma outra mudança de schema. Todas as FKs continuam exatamente como estão.

## Fluxo

### 1. Agendar

1. Admin abre **Configurações → Sistema → Zona de Perigo → Excluir esta igreja**.
2. Modal busca um resumo (`GET /igrejas/exclusao/resumo`) e mostra contagens reais: *"Isso
   vai apagar definitivamente N pessoas, N eventos, N movimentações financeiras, N células,
   N ministérios, N usuários…"*. Se a igreja for mãe, uma linha extra: *"As N igrejas
   vinculadas ([nomes]) vão sair da rede — cada uma continua funcionando normalmente, com
   todos os dados intactos, só deixam de estar ligadas a esta."* (wording provisório —
   "rede" é o termo desta igreja; quando existir personalização de rótulo por igreja/
   denominação, isso vira configurável).
3. Deixa claro que é reversível por 10 dias e como cancelar.
4. Campo pra digitar o nome exato da igreja.
5. Reautenticação: campo de senha (login nativo) ou botão "Confirmar com Google" (login só
   Google).
6. `POST /igrejas/exclusao/agendar` — valida nome + reautenticação, seta
   `exclusao_agendada_em = NOW()` e `exclusao_agendada_por_usuario_id`, dispara e-mail
   "agendada".

Se já existe uma exclusão agendada, o botão na Zona de Perigo vira "Cancelar exclusão" —
não dá pra agendar duas vezes.

### 2. Durante os 10 dias

- Banner fixo, só pro `ADMIN_IGREJA`, em toda tela: *"Esta igreja será excluída
  definitivamente em N dias. [Cancelar exclusão]"*.
- `GET /igrejas/minha` (já existe) ganha `exclusaoAgendadaEm` / `diasRestantes` pro front
  montar o banner.
- `POST /igrejas/exclusao/cancelar` — zera as duas colunas, sem reautenticação.
- Sistema funciona 100% normal pra todo mundo, sem exceção.

### 3. Job diário

`@Scheduled` (mesmo padrão do `LimpezaFotosJob`), roda uma vez por dia. Pra cada igreja com
`exclusao_agendada_em` setado:

- Faltam exatamente 5 dias → e-mail de lembrete.
- Falta exatamente 1 dia → e-mail de lembrete final.
- Prazo já venceu → executa a purga completa (seção seguinte).

### 4. Purga (tabela por tabela, ordem explícita)

Tudo dentro de **uma transação** (rollback total se qualquer passo falhar — a igreja
continua "agendada" e o job tenta de novo no dia seguinte):

1. `acompanhante_inscricao` → `inscricao_evento`
2. `movimentacao_contribuinte` → `movimentacao_financeira` → `categoria_financeira`
3. `celula_membro` → `celula`
4. `ministerio_membro` → `ministerio`
5. `usuario_capacidade`
6. `evento`
7. `visitante`, `local_evento`
8. **Fotos**: pra cada `foto` da igreja, apaga o arquivo no R2 primeiro, depois a linha no
   banco. Best-effort — falha numa foto loga alto e segue, não trava a purga inteira.
9. Se a igreja é mãe: desvincula todas as congregações (mesma lógica de
   `VinculoService.sairDaFamilia`, em lote — nova função, não existe ainda)
10. `usuario`, depois `pessoa`
11. **Elasticsearch**: apaga os documentos de todos os índices (pessoa, evento, usuário,
    movimentação, categoria, célula, ministério, visitante) filtrando por `igreja_id`
12. A linha da própria `igreja`

A ordem exata (1–7) será conferida contra o schema real na hora de implementar — o que
importa aqui é o princípio: passo a passo, explícito, logado, nunca dependente de cascade
do banco.

R2 (passo 8) e Elasticsearch (passo 11) ficam **fora** da transação Postgres — não dá pra
fazer rollback de um bucket ou de um índice. São melhor-esforço: falha é logada bem alto
pra reconciliação manual depois, mas não desfaz nem trava a purga do banco.

Depois da purga, o e-mail de "exclusão concluída" é disparado — precisa ser enviado **antes**
do passo 12 (a linha da igreja, com o e-mail de contato, ainda precisa existir).

## Endpoints novos

- `GET /igrejas/exclusao/resumo` — contagens pro modal (`ADMIN_IGREJA` only)
- `POST /igrejas/exclusao/agendar` — body: nome digitado + senha OU idToken do Google
- `POST /igrejas/exclusao/cancelar`
- `GET /igrejas/minha` (existente) ganha `exclusaoAgendadaEm` / `diasRestantes`

## Casos de borda

- Senha errada / Google não bate o `google_sub` → erro claro, nada é agendado.
- Purga falha no meio → rollback total, tenta de novo no dia seguinte.
- Prazo vence mas ninguém loga mais → não importa, o job roda sozinho, sem depender de sessão.
- Igreja sem família (não é mãe nem filha) → pula o passo 9, segue direto pra purga normal.
- Depois da purga, qualquer tentativa de login daquela igreja recai no erro normal de
  credencial inválida (a linha simplesmente não existe mais) — nada de tratamento especial.

## Testes

O teste mais importante do projeto: um `@SpringBootTest` que cria **um registro de cada
tipo** (pessoa, evento, inscrição+acompanhante, movimentação+contribuinte, categoria,
célula+membro, ministério+membro, usuário, visitante, local, foto) numa igreja de teste,
roda a purga, e verifica que **tudo sumiu sem nenhum erro de FK**.

Complementar: agendar/cancelar, reautenticação (senha certa/errada, Google), desvínculo de
família em lote, e-mails disparados nos momentos certos (agendamento, 5 dias, 1 dia,
conclusão).

## Estratégia de implementação

Maior raio de alcance do sistema — implementar com **subagentes revisando**, em pedaços:

1. Schema (migration) + agendar/cancelar + job de verificação de prazo (sem a purga em si)
2. Purga por módulo (financeiro → célula/ministério → evento → foto/R2 → usuário/pessoa →
   Elasticsearch → igreja)
3. Reautenticação (senha + Google)
4. Front: modal de confirmação, banner de contagem regressiva, resumo de contagens

Cada pedaço revisado antes do próximo, não tudo de uma vez.

## Fora do escopo desta versão

- Excluir a família inteira (todas as igrejas vinculadas) de uma vez — fica pra quando
  pagamento/planos existirem.
- Personalizar o termo "rede" por igreja/denominação — fica pra feature futura de
  personalização de rótulos.
- Exportar/baixar os dados antes de excluir (o backup diário do Postgres já cobre disaster
  recovery a nível de banco; um export granular por igreja é nice-to-have, não pedido).
