# Eventos compartilhados entre igrejas vinculadas — design

> Revisão do brainstorm original de 2026-07-25, refinado em 2026-07-28. Reaproveita o
> modelo de "família de igrejas" (sede/congregações, `igreja.igreja_mae_id`) já existente
> — não inventa hierarquia nova.

## Motivação

Igrejas com congregações fazem eventos comuns o tempo todo (conferência conjunta,
retiro, encontro de jovens) — hoje cada evento é isolado dentro de uma igreja
(`igreja_id`), sem jeito de uma pessoa de uma congregação ver ou se inscrever num
evento criado pela sede (ou por outra congregação da família), e vice-versa.

## Risco desta feature

Isso mexe na garantia mais sensível do sistema — o isolamento por `igreja_id`. Ao
contrário de módulos novos isolados (Ministério, Célula, Visitantes), aqui a mudança é
**abrir uma exceção controlada** nesse isolamento, não construir algo novo por cima
dele. Um erro aqui vaza dado entre igrejas (já aconteceu um quase-vazamento parecido no
recurso de igrejas vinculadas, ver memória do projeto `igrejas-vinculadas-familia`). A
igreja piloto **já tem família real vinculada em produção** — qualquer decisão de
migração que exponha dado por padrão afeta dado real, não um cenário hipotético.

## Modelo de dados

Um campo novo em `evento`:

```sql
ALTER TABLE evento ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT false;

-- Preserva o comportamento atual para dado já existente: nenhum evento passado
-- vira compartilhado sem uma escolha explícita de quem o criou.
UPDATE evento e
SET restrito_propria_igreja = true
WHERE EXISTS (
    SELECT 1 FROM igreja i
    WHERE i.id = e.igreja_id
      AND (i.igreja_mae_id IS NOT NULL OR EXISTS (SELECT 1 FROM igreja f WHERE f.igreja_mae_id = i.id))
);
```

- `false` (padrão para eventos **criados a partir deste deploy**): visível e
  inscrevível por toda a família de igrejas — qualquer igreja da família pode criar um
  evento compartilhado, visível às demais (não é hierárquico: sede compartilha com
  congregações e vice-versa, e entre congregações irmãs também).
- `true`: evento visível/inscrevível só pela própria igreja — comportamento de hoje. É
  o valor atribuído pela migration a **todo evento já existente** de uma igreja que já
  tem família (sede ou congregação), pra ninguém ter dado exposto sem ter escolhido.
- Nenhuma outra coluna nova. Vagas, elegibilidade (`idade_min`/`idade_max`,
  `restricao_estado_civil`, `restricao_sexo`, `exclusivo_membros`) continuam lendo só os
  campos que já existem em `pessoa`, sem qualquer distinção por igreja — ver seção
  "Elegibilidade e vagas" abaixo.

### UI do toggle

O campo "Apenas minha igreja" só aparece no formulário de evento se a igreja **tem
família** (tem `igreja_mae_id` preenchido, ou tem alguma igreja filha) — uma igreja
independente nem vê a opção, já que não tem com quem compartilhar.

**Decisão deliberada: sem interruptor global por igreja.** Cogitamos um botão em
Configurações (ou na aba de Eventos) pra ligar/desligar compartilhamento pra todos os
eventos de uma vez, mas descartamos por ora: a aba de Configurações é da Fase 3 (ainda
não existe) e um interruptor mestre criaria uma regra de precedência a mais (o que vale
quando ele e o toggle do evento discordam?) pra resolver um problema que o toggle por
evento já cobre sozinho. Se, no uso real, a igreja piloto reclamar de marcar o toggle
toda vez, a solução mais barata é o formulário lembrar a última escolha (front, sem
schema novo) — não um dado de configuração por igreja.

## Família de igrejas — método bidirecional novo

O mecanismo existente, `FamiliaIgrejaService.idsDaFamilia(igrejaId)`, enxerga só pra
baixo (`{eu} ∪ {minhas filhas}` — comentário no código: "a filha nunca vê a mãe nem as
irmãs"). Isso serve bem ao financeiro consolidado (visão de quem gerencia a sede), mas
não ao caso de eventos, que precisa ser bidirecional.

**Decisão: método novo, não altera o existente.** `idsDaFamilia` continua exatamente
como está, usado só pelo financeiro consolidado. Adicionamos
`FamiliaIgrejaService.idsDaFamiliaCompleta(igrejaId): Set<UUID>`:

- Se a igreja **tem mãe**: família = `{mãe} ∪ {todas as filhas da mãe, inclusive eu}`.
- Se a igreja **não tem mãe** (é sede ou é independente): família = `{eu} ∪ {minhas
  filhas}` — mesmo resultado do método existente, só que como caso particular de uma
  regra simétrica.

A regra dos "2 níveis" já em vigor (quem tem mãe não pode ser mãe) garante que esse
algoritmo não precisa recursão nem tratar profundidade arbitrária. Método pensado pra
ser reaproveitado por outros módulos futuros que precisem da mesma noção simétrica de
família (fora do escopo deste spec — só a assinatura fica pronta pra isso).

## Autorização

- **Gerenciar o evento** (editar, arquivar, alterar o toggle de restrição, inscrever
  outros, controlar presença, cancelar inscrição de terceiros): `podeGerenciarEventos(role)`
  **E** `usuarioAutenticado.getIgrejaId().equals(evento.getIgreja().getId())`. Uma pessoa
  com role alta (`ADMIN_IGREJA`/`LIDER`) em **outra** igreja da família não ganha nenhum
  poder de gestão sobre um evento que não é da própria igreja — só visualiza e se
  inscreve, igual qualquer pessoa comum.
- **Ver e se inscrever**: qualquer pessoa autenticada cuja igreja esteja em
  `idsDaFamiliaCompleta(evento.igrejaId)` — sempre a própria igreja criadora; as demais
  só quando `restrito_propria_igreja = false`.
- **Cancelar a própria inscrição**: sempre permitido, própria igreja ou não — não muda
  em relação a hoje.

Implementação: os métodos que já checam `podeGerenciarEventos`/`podeGerenciarInscricoes`
no `EventoController`/`InscricaoController` passam a também comparar
`usuarioAutenticado.getIgrejaId()` contra `evento.getIgreja().getId()` antes de liberar
qualquer ação de gestão — se a igreja for diferente, `AccessDeniedException` mesmo que
a role permitisse dentro da própria igreja.

## Visibilidade — listagem, detalhe e busca

- **`GET /eventos`**: `EventoRepository.buscarPorIgreja` hoje filtra por um único
  `igreja_id`. Passa a receber a própria igreja (visível por completo) e o restante da
  família (`idsDaFamiliaCompleta(minhaIgreja) - {minhaIgreja}`, só os eventos com
  `restrito_propria_igreja = false`): `WHERE igreja_id = :minhaIgreja OR (igreja_id IN
  :restoDaFamilia AND restrito_propria_igreja = false)`. `EventoResponse` ganha
  `igrejaOrganizadora: {id, nome, sigla}`; o front só mostra o badge quando
  `igrejaOrganizadora.id` é diferente da minha igreja, usando a sigla ou, se ela for
  nula, o nome completo.
- **`GET /eventos/{id}`** — mesma regra de visibilidade: pessoa de fora vê tudo que já vê
  hoje (elegibilidade, vagas, descrição); o DTO de detalhe ganha
  `podeGerenciarEsteEvento: boolean` (calculado no back pela regra de autorização acima)
  pra o front decidir o que mostrar sem recalcular a regra localmente.
- **Busca global (Elasticsearch)**: o índice `EventoDocument` ganha o campo
  `restritoPropriaIgreja` (hoje não existe lá) — continua guardando o `igreja_id` real
  do evento (não duplica por igreja). `BuscaEventoService.buscar` passa a filtrar por
  `term("igrejaId", minhaIgreja) OR (terms("igrejaId", restoDaFamilia) AND
  term("restritoPropriaIgreja", false))` em vez de só "meu `igreja_id`".

## Inscrição, vagas e elegibilidade

- **Vagas**: cota única para o evento inteiro, sem reserva por igreja — quem se
  inscrever primeiro ocupa vaga, não importa a igreja. O lock pessimista de contagem
  de vagas já existente não muda (continua contando todos os inscritos confirmados do
  evento, independente de qual igreja a pessoa é).
- **Elegibilidade** (`idade_min`/`idade_max`, `restricao_sexo`,
  `restricao_estado_civil`, `exclusivo_membros`): avalia os dados da própria pessoa
  (`data_nascimento`, `sexo`, `estado_civil`, `vinculo`) — **não** distingue por
  igreja. Um membro batizado (`vinculo = MEMBRO`) de uma congregação passa no filtro
  "exclusivo membros" de um evento criado pela sede, e vice-versa: o critério é sobre a
  pessoa (é batizada?), não sobre a igreja dela. **Nenhuma mudança na lógica de
  elegibilidade em si** — ela já lê só campos de `pessoa`.
- **`POST /eventos/{id}/inscricoes`**: a checagem de visibilidade
  (`restrito_propria_igreja` + família) entra ANTES da elegibilidade normal — pessoa de
  fora da família tentando se inscrever num evento restrito recebe 404 (mesmo
  tratamento de "não vê o recurso" — não 403, pra não revelar que o evento existe),
  antes mesmo de chegar nas regras de idade/sexo/etc.

## Lista de inscritos / participantes — rastreio

Cada linha da lista de inscritos e do relatório de presença por evento ganha o
nome/sigla da igreja da pessoa (join com `pessoa.igreja_id → igreja`) — **sempre
presente no dado retornado pela API**; o front só destaca a coluna/badge quando o
evento é compartilhado (`restrito_propria_igreja = false`) ou quando há de fato mais de
uma igreja distinta entre os inscritos. Isso resolve o problema de confusão que motivou
o spec: numa lista com gente de igrejas diferentes, fica claro de onde é cada um.

## Frontend

- **Formulário de evento**: checkbox "Apenas minha igreja", visível só se a igreja tem
  família.
- **Card de evento** (listagem): badge com sigla (ou nome, se sem sigla) da igreja
  organizadora, mostrado só quando ela é diferente da igreja de quem está vendo.
- **Detalhe do evento**: botões de gestão (editar, controlar presença, inscrever
  outros) condicionados a `podeGerenciarEsteEvento` vindo da API.
- **Lista de inscritos / relatório de presença**: coluna/badge de igreja, destacada
  visualmente só quando o evento é compartilhado ou a lista tem mais de uma igreja.
- **Busca global**: sem mudança visual além do resultado aparecer pra quem é de outra
  igreja da família — reaproveita o mesmo badge de "criado por" do card de evento.

## Testes

- `FamiliaIgrejaServiceTest`: `idsDaFamiliaCompleta` nos três casos — igreja com mãe
  (vê mãe + irmãs), igreja sede (vê a si + filhas), igreja independente (só vê a si
  mesma).
- `EventoServiceTest`: evento compartilhado de outra igreja da família aparece na
  listagem; evento restrito de outra igreja da família NÃO aparece; evento da própria
  igreja sempre aparece (restrito ou não).
- `InscricaoServiceTest`: pessoa de outra igreja da família se inscreve com sucesso num
  evento compartilhado; pessoa de fora da família (ou de dentro, evento restrito)
  recebe erro de "não encontrado" antes de chegar na elegibilidade; role alta
  (admin/líder) de outra igreja da família tentando gerenciar o evento recebe
  `AccessDeniedException`.
- Migration: teste (ou validação manual, seguindo o padrão do projeto pra migration)
  confirmando que eventos existentes de igreja com família viram `restrito_propria_igreja
  = true`, e que igreja sem família nem entra no `UPDATE`.

## Fora de escopo

- **Interruptor global de compartilhamento por igreja** (decisão desta revisão,
  2026-07-28) — o toggle por evento já resolve o caso de uso; revisitar só se o uso real
  pedir, e nesse ponto a aba de Configurações (Fase 3) já deve existir.
- **Responsável/gerente específico por evento** (autorização por recurso, no estilo de
  Ministério/Célula — "fulano é responsável por este evento X"). Hoje é só o gate de
  role + mesma igreja; delegar gestão a alguém específico (inclusive de outra igreja)
  fica pra uma entrega futura, se fizer sentido.
- **Reserva de vagas por igreja** (cota separada por igreja dentro do mesmo evento) —
  a cota é sempre única.
- **Cobrança/pagamento** — `preco` continua só informativo (decisão já tomada no
  roadmap, Fase 6, para eventos em geral).
- **Quebra do relatório geral de eventos por igreja** (o relatório de engajamento já
  existente, `EventoRelatorioService`, não ganha uma dimensão "por igreja" neste spec —
  só a lista de inscritos e o relatório de presença por evento específico ganham o
  rastreio). Fica pra uma entrega separada, se o uso real pedir — mesmo padrão que já
  existe hoje pro financeiro consolidado (`VisaoGeralCongregacoes`), reaproveitável
  quando essa entrega acontecer.
