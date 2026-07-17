# Design — Igrejas vinculadas (mãe e congregações)

- **Data:** 2026-07-16
- **Fase:** 2 (funcionalidades de valor para a igreja) — item novo, não estava no roadmap
- **Status:** design aprovado, pendente plano de implementação
- **Implementação:** **depois de fechar a Fase 1** (backup, hospedagem/prod, CI/CD, Resend)

> ⚠️ **ANTES DE IMPLEMENTAR: reler este spec com o autor.** Pedido explícito dele em
> 2026-07-16 — o design foi fechado numa conversa longa e ele quer reconferir as decisões
> com calma antes de virar código.

**Validado com o pastor:** o autor já conversou com o pastor sobre esta feature antes deste
design, e o que ele descreveu aqui é o que o pastor pediu. **O essencial é o relatório dos
números de todas as congregações** — isso está confirmado, não é suposição. A única dúvida em
aberto do lado do pastor é se ele vai querer **abrir a lista de membros/eventos** de cada
congregação; o autor decidiu que isso se atualiza depois (ver "Fora de escopo").

## Problema

O piloto não é uma igreja: são **4** — a **sede (mãe)** e **3 congregações**. Todas usarão o
Domus, cada uma gerenciando a si mesma, mas a sede precisa **observar** o conjunto: os
números de cada congregação separadamente **ou** somados, à escolha.

Isso pede, pela primeira vez, **acesso cruzado controlado** entre igrejas — o oposto exato
da regra que sustenta o sistema hoje ("isolamento lógico por `igreja_id`, defesa contra
acesso cruzado entre igrejas"). Por isso mexe no alicerce e precisa vir **antes** dos outros
itens da Fase 2 (endereço estruturado, evento, configurações), que se apoiam neste modelo.

Levantamento: `igreja_id` aparece **349 vezes em 49 arquivos** e viaja dentro do JWT.

**A mãe é uma igreja operante**, igual às outras — tem membros, eventos e financeiro
próprios. O consolidado é, portanto, **mãe + filhas**.

## Decisão central: auto-referência, não tabela nova

`igreja` ganha `igreja_mae_id UUID NULL` apontando para `igreja(id)`.

**Por quê:** uma congregação **é** uma igreja — mesmos campos (nome, CNPJ, e-mail, telefone).
O guardrail do próprio roadmap: *"tabela nova é para N-para-N ou dado repetido/compartilhado
— não para 1-para-1."*

O ganho decisivo: **congregação é só uma igreja que tem mãe**, então as **349 checagens de
isolamento continuam intactas**. Cada congregação segue sendo um tenant isolado. A hierarquia
apenas **acrescenta** consultas na visão da mãe; não reescreve nenhuma existente.

**Alternativa descartada — tabela `igreja_filha`:** seria cópia quase idêntica de `igreja`, e
o `Membro` passaria a apontar para "uma igreja **ou** uma congregação" (relação polimórfica),
dobrando cada uma das 349 consultas. Sistema dobrado para representar a mesma coisa com dois
nomes.

### Regra dos 2 níveis: quem tem mãe não pode ser mãe

Entrega três coisas de uma vez:

1. **Impossibilita ciclo** (A mãe de B, B mãe de A).
2. **A consulta deixa de ser recursiva:** a família sai de
   `WHERE id = :meuId OR igreja_mae_id = :meuId`. Sem CTE recursiva.
3. **Reflete a realidade**: sede e congregações, não árvore genealógica.

**Onde vive:** na camada de **serviço**, no momento do vínculo. Sem trigger no banco — o
projeto não usa nenhum, e como a consulta não é recursiva, um ciclo hipotético não derruba
nada. O custo de errar não justifica a complexidade.

## Decisão: self-service com código de vínculo

O autor quer o fluxo **pronto para vender** (não só para o piloto): cada igreja se cadastra
sozinha e o vínculo é feito depois.

### A direção do consentimento (o que descartou a alternativa)

Chegou-se a propor "a mãe cria a congregação" (mais simples). **Está errado**, e o motivo
importa: quem se expõe é a **filha** — o financeiro dela é que aparece para a mãe. Logo,
**quem precisa consentir é a filha**. Se a mãe pudesse declarar "aquela é minha congregação",
qualquer igreja se declararia mãe de outra e leria financeiro alheio.

O código acerta a direção nos dois lados: a mãe **gera** o código (consente em receber); a
filha **digita** (consente em se expor).

### Alternativa descartada — lista de igrejas para escolher a mãe

Vazaria a **base de clientes inteira**: qualquer um abre o cadastro e vê todas as igrejas que
usam o Domus. Inaceitável para um produto que será vendido.

### Alternativa adiada — solicitação + aprovação

A filha busca a mãe (por CNPJ/e-mail exato, nunca lista), pede, a mãe aceita/rejeita. Mais
explícito, mas exige entidade de solicitação com estados, tela de pendências, notificação e
tratamento de pedido rejeitado/expirado. Todo esse custo vai para a **negociação**, não para
o **vínculo**. É uma camada por cima do que este design entrega — dá para adicionar depois
sem refazer nada. Convite por e-mail idem (Fase futura).

### O código

- **8 caracteres**, exibido como `XK4P-2M7Q`. Coluna `codigo_vinculo VARCHAR(9) NULL UNIQUE`.
- **Alfabeto sem ambíguos**: sem `0`/`O`, sem `1`/`I`/`L`. Restam 32 símbolos → 32⁸ ≈ 1
  trilhão. O código será **ditado no WhatsApp ou lido num papel**; `Q` virando `O` é chamado
  de suporte.
- **Reutilizável** (a mãe tem 3 congregações) e **rotacionável** (gerar de novo invalida o
  anterior). **Sem expiração.**
- **Gerar código NÃO torna a igreja mãe.** "Ser mãe" é ter **pelo menos uma filha vinculada**
  (`EXISTS filha WHERE igreja_mae_id = eu`), não possuir um código. Consequência: uma igreja
  que gerou código e ninguém usou continua **independente** — a aba "Congregações" em
  Relatórios não aparece para ela (não haveria o que mostrar), e ela ainda pode desistir e
  entrar na família de outra. A aba vira visível no instante em que a primeira filha entra.

**O risco é contraintuitivo:** se o código vazar, um estranho pendura a igreja dele na
família — e **expõe os dados dele para a mãe**. O atacante se machuca. O dano real é sujar o
consolidado com números de terceiro. Por isso a defesa não é uso único nem expiração, e sim
**desvincular** + **rotacionar**: controle depois do fato, proporcional ao problema.

### Desvincular: os dois lados podem

A mãe remove uma congregação; a congregação sai da família. **Quem consente pode revogar** —
a filha expôs o financeiro dela por consentimento. Se só a mãe pudesse desfazer, seria uma
armadilha: entra fácil, nunca mais sai. Com LGPD no horizonte e igrejas reais envolvidas, é
o desenho certo.

### Validações no momento do vínculo (todas no serviço)

- O código existe?
- A dona do código já tem mãe? (violaria os 2 níveis)
- Quem está entrando já tem filhas? (violaria os 2 níveis)
- É ela mesma? (auto-vínculo)

Qualquer uma → recusa.

## Autorização (o coração — um erro aqui vaza financeiro entre igrejas)

Serviço novo que responde três perguntas, **sempre calculando no servidor**:

- *Sou mãe?* (tenho filhas?)
- *Quais os ids da minha família?* → `{minha igreja} ∪ {minhas filhas}`, **e só se eu for
  mãe**. Se sou filha ou independente, a resposta é `{eu}`.
- *Essa igreja que me pediram pertence à minha família?*

**A regra que não pode ser quebrada:** o id de **quem pergunta** vem do JWT (como já é hoje).
O id de **quem se quer ver** vem da requisição e **tem que ser validado** contra a família.
Sem isso é **IDOR**: a mãe da família A pede `igrejaId` da família B e lê financeiro de
estranho. **É o risco número um desta feature.**

- **Quem enxerga:** só `ADMIN_IGREJA` (coerente com o financeiro ser ADMIN-only hoje).
- **A hierarquia enxerga só para baixo:** a filha nunca vê a mãe nem as irmãs.

## Relatórios

**Escopo aprovado: números e recortes. Sem listas navegáveis.**

O que já existe (`RelatorioService`) é só financeiro: `resumoPorPeriodo`, `porCategoria`,
`evolucaoMensal`, `maiorLancamento`. **Relatórios de membros e eventos não existem** e entram
como contagens.

### Campos verificados no código

- **`StatusMembro` tem TRÊS valores: `ATIVO`, `INATIVO`, `VISITANTE`** — não dois. Visitante
  é justamente o número de funil que pastor acompanha; não pode ser escondido.
- **`Evento` tem `inicioEm`/`fimEm`** → realizados vs. próximos sai sem criar campo.
- Ambos têm soft delete (`deleted_at` + `@SQLRestriction`), respeitado automaticamente.

### Endpoints

- Os **4 relatórios financeiros existentes** ganham `igrejaId` **opcional**. Ausente → minha
  igreja (**comportamento atual intacto**). Presente → valida a família e escopa.
- **Um endpoint novo** devolve a tabela consolidada: por igreja e somado — membros por
  status, eventos por realizado/próximo, e entradas/saídas/saldo.

### Consultas

**3 consultas com `GROUP BY igreja_id`** (uma para membros, uma para eventos, uma para
financeiro), depois montagem em memória. Não é otimização prematura: é a forma natural de
escrever — um laço com uma consulta por congregação daria **mais** código, não menos.

## Front

| Onde | O quê | Nome da aba |
|---|---|---|
| **Configurações** | gerar código, entrar com código, lista de congregações, desvincular | **"Igrejas vinculadas"** (serve para mãe e filha) |
| **Relatórios** (tela que já existe) | consolidado, por-igreja e o seletor | **"Congregações"** (só aparece para a mãe) |

**Por que separado:** configurar o vínculo é configuração; ver números é relatório. O pastor
vê os números **onde ele já vai ver números**.

A aba **Congregações** (dentro de Relatórios) tem **duas camadas**, conforme o mockup
aprovado:

1. **Visão geral** — o consolidado e a tabela por-igreja **juntos, na mesma tela**:

```
FAMÍLIA (consolidado)
  Membros ....... 210  (198 ativos / 12 inativos / 9 visitantes)
  Eventos ....... 15   (11 realizados / 4 próximos)
  Entradas ...... R$ 20.500,00
  Saídas ........ R$  8.100,00
  Saldo ......... R$ 12.400,00

POR IGREJA          membros    eventos      saldo
  Sede             120 (114/6)  8 (6/2)   R$ 8.100,00
  Congregação A     45 (42/3)   3 (2/1)   R$ 2.300,00
  ...
```

2. **Detalhe de uma igreja** — escolher uma linha/seletor abre os **4 relatórios financeiros
   que já existem**, escopados naquela igreja (é aqui que entra o `igrejaId` opcional).

O "individual ou somatório, você escolhendo" acontece nas duas camadas: o somatório está no
topo da visão geral; o individual, na tabela e no detalhe.

Em **Configurações → Igrejas vinculadas**, três estados **mutuamente exclusivos** (a regra
dos 2 níveis faz a exclusividade cair de graça — a tela nunca oferece o impossível):

| Estado | Conteúdo |
|---|---|
| **Independente** | "Gerar código para minhas congregações" **ou** "Entrar numa família: [código]" |
| **Mãe** | Código + lista de congregações, cada uma com "desvincular" |
| **Filha** | "Você é congregação de *Igreja Sede*" + "Sair da família" |

Visível só para `ADMIN_IGREJA`.

**Dependência declarada:** a aba de **Configurações não existe ainda** (Fase 3 do roadmap).
Ela precisa vir antes. Não é escopo novo — o autor já a queria — mas é sequência.

## Migração

`igreja_mae_id` nulo = igreja independente, que é o estado de **todas** hoje. A migration
**não altera dado nenhum**: só adiciona duas colunas nuláveis. Sem downtime, sem backfill.

## Testes

Convenção do projeto: Mockito puro, sem contexto Spring. Testes de repositório contra o
Postgres real quando envolver consulta (ver a lição de `principal-desanexado-lazy`).

- **IDOR (o mais importante):** mãe da família A pedindo `igrejaId` da família B → recusa.
- Filha pedindo dados da mãe → recusa. Filha pedindo dados de irmã → recusa.
- `igrejaId` ausente → comportamento de hoje, intacto.
- Vínculo: código inexistente, mãe que já tem mãe, filha que já tem filhas, auto-vínculo →
  todos recusados.
- Rotação: código antigo para de funcionar.
- Desvincular pela mãe e pela filha.
- Contagens: os **três** status de membro; eventos realizados vs. próximos na virada da data.
- Consolidado = mãe + filhas (a mãe opera, não pode ficar de fora da soma).

## Critério de pronto

- A sede vê os números das 3 congregações, individualmente e somados.
- Nenhuma congregação enxerga a mãe ou as irmãs.
- Uma igreja de outra família nunca aparece, nem forçando `igrejaId` na requisição.
- As 349 checagens de `igreja_id` seguem intactas.
- Vínculo e desvínculo funcionam pelos dois lados.

## Fora de escopo (anotar no BACKLOG)

- **Listas navegáveis** (a mãe folhear membros/eventos das filhas). Adiado **de propósito**:
  abre a decisão "a mãe pode *editar* o membro da congregação?", exige telas somente-leitura
  ou autorização nova, e um indicador permanente de contexto para ninguém cadastrar na igreja
  errada. **É justamente a feature sobre a qual o pastor deve ser consultado primeiro** —
  construí-la antes é o caso mais puro de "feature construída no escuro". Nada é jogado fora
  ao adiar: o vínculo, a validação de família e o seletor vêm neste design.
- **Irmãs verem eventos umas das outras** (nunca financeiro) — pedido do autor para depois.
- **Solicitação + aprovação** e **convite por e-mail** — camadas por cima do vínculo.
- **Congregação de congregação** (3+ níveis) — a regra dos 2 níveis é deliberada.
