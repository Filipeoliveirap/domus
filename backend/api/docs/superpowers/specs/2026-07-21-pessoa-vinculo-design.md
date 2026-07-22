# Pessoa e vínculo — design

**Data:** 2026-07-21
**Origem:** conversa do autor com o pastor da igreja piloto
**Natureza:** mudança de modelo de domínio (renomeia tabela, muda semântica, atravessa o sistema)

## O problema

No vocabulário cristão, **"membro" significa pessoa batizada**. A tabela `membro` do Domus guarda
todo mundo — batizados, frequentadores não batizados e visitantes. O nome mente.

O pastor apontou a confusão na prática: se a tela chama "Membros", a lista deveria ter só os
batizados. Como tem todo mundo, a contagem que o Domus mostra não é a contagem que a igreja usa.

E havia um segundo problema, mais escondido: **o campo `status` fazia dois trabalhos**.

```
ATIVO | INATIVO | VISITANTE
```

`VISITANTE` responde *"que vínculo tem com a igreja"*; `ATIVO`/`INATIVO` respondem *"ainda
frequenta"*. Eixos independentes num campo só — e é por isso que "membro ativo" soava certo e
estava errado.

## A decisão

**Um campo, dois valores:**

```
pessoa.vinculo = MEMBRO | CONGREGANTE
```

- **MEMBRO** = batizado, formalmente membro da igreja.
- **CONGREGANTE** = frequenta, não é batizado. Absorve o antigo `VISITANTE` — para a igreja do
  piloto, quem visita e quem congrega são a mesma categoria administrativa.

**`batizado` (boolean) deixa de existir.** Era redundante: `MEMBRO` já significa batizado. Manter
os dois permitiria o estado impossível "membro não batizado".

**`INATIVO` deixa de existir.** Decisão do autor, e ele está certo: a igreja não guarda inativo —
**arquiva**. O soft delete (`deleted_at`) já é esse eixo, e a Fase 3 já prevê a tela de
arquivados. Criar um `situacao ATIVO|INATIVO` seria um segundo mecanismo para o que já existe.

**`data_batismo` continua**, opcional, exibida no formulário **apenas quando `vinculo = MEMBRO`**.
Obrigatória travaria o cadastro de quem é membro há trinta anos e ninguém sabe a data — e a
secretaria inventaria uma para conseguir salvar.

## Os nomes

**Tabela e entidade: `pessoa`.** O nome deve dizer *o que a linha é*, não *o que ela representa
para a igreja* — assim ele não fica errado quando a relação muda. `cadastro` foi descartado por
descrever o registro e não o ser humano: "o cadastro se inscreveu no evento" some com a pessoa
da frase.

Ganho de brinde: a regra do projeto era *"todo usuário é um membro"*, que a descoberta do pastor
tornou falsa (dá para ter login sem ser batizado). Vira **"todo usuário é uma pessoa"** — verdade
sem nota de rodapé.

**Rótulo na interface: "Pessoas"**, com filtro separando Membros e Congregantes.

**Role `MEMBRO` → `ACESSO_COMUM`.** As roles falam de nível de permissão, não de vínculo com a
igreja — hoje um congregante com login recebe a role "MEMBRO", que é exatamente a confusão que
esta spec elimina. `USUARIO` foi descartado por colidir com a tabela `usuario`: todo mundo com
login já é usuário, então "usuário com role de usuário" é circular. `ACESSO_COMUM` segue o
padrão de `ADMIN_IGREJA` (substantivo composto) e contrasta com `LIDER`.

## Perfis: perguntar pela capacidade, não pela identidade

Renomear `MEMBRO` dói hoje por um motivo que **não é o nome**: a permissão é perguntada como
identidade, espalhada por dezenas de arquivos.

```java
"ADMIN_IGREJA".equals(role) || "LIDER".equals(role)   // back
role === 'ADMIN_IGREJA' || role === 'LIDER'           // front
```

Isso pergunta *"quem é você?"* quando o código quer saber *"você pode gerenciar inscrições?"*.
Cada lugar reimplementa a mesma regra, e uma discordância entre dois deles é um furo de
autorização silencioso — não um erro de compilação.

**Esta spec inclui a extração dessa camada**, porque o autor já avisou que o nome vai mudar de
novo e porque o rename atual é a oportunidade barata de arrumar (os arquivos já vão ser tocados).

**Backend**
- `enum Role { ADMIN_IGREJA, LIDER, ACESSO_COMUM }` — acaba a string crua.
- Um ponto único de política (ex.: `Permissoes`) com métodos nomeados pela **ação**:
  `podeGerenciarInscricoes`, `podeVerDadosSensiveisDePessoa`, `podeVerListaCompletaDeInscritos`,
  `podeGerenciarPessoas`. Services e controllers chamam esses métodos; nunca comparam string.
- `SecurityConfig` referencia o enum, não literais.

**Frontend**
- `lib/permissoes.ts` com as mesmas perguntas, no mesmo vocabulário do backend. Componentes
  chamam `podeGerenciarInscricoes(role)`; nenhum `role === '...'` sobrevive fora desse arquivo.

**Critério de pronto:** trocar o nome de um perfil toca **um arquivo de cada lado**. E o nome do
método diz a regra — ler `podeVerDadosSensiveisDePessoa` explica o porquê; ler
`role === 'ADMIN_IGREJA'` não.

⚠️ **A checagem continua sendo do servidor.** A camada do front existe para a interface não
oferecer o que vai falhar — não é autorização. O backend valida de novo, sempre.

## Reset das migrations

Decidido com o autor: **apagar V1–V16 e escrever uma V1 única já com os nomes certos**, em vez de
uma V17 de rename.

Justificativa: não há dado real ainda. Uma migration de rename carregaria para sempre a cicatriz
do nome antigo (`ALTER TABLE membro RENAME TO pessoa`, FKs, índices, constraints em quatro
tabelas), e todo `\d pessoa` futuro mostraria constraints chamadas `fk_usuario_membro`.

**Consequências aceitas:**
- Banco de **produção** e de **dev** são derrubados e recriados. A conta do autor em produção se
  perde; ele recadastra.
- Redeploy obrigatório (o schema muda).
- Backups anteriores no R2 ficam **irrestauráveis** contra o código novo. Aceitável: não há dado
  real. O ensaio de restauração trimestral passa a valer a partir do primeiro backup novo.
- Dev precisa ser repovoado.

**Seguro barato:** tirar um `pg_dump` de dev e de prod antes de derrubar, e guardar fora do
projeto. Custa dois comandos e cobre o arrependimento.

## Onde a mudança bate

| Área | Hoje | Depois |
|---|---|---|
| Tabela | `membro` | `pessoa` |
| Vínculo | `status ATIVO\|INATIVO\|VISITANTE` + `batizado` | `vinculo MEMBRO\|CONGREGANTE` |
| FKs | `membro_id` em `usuario`, `movimentacao_financeira`, `inscricao_evento` | `pessoa_id` |
| Role | `MEMBRO` | `ACESSO_COMUM` |
| Rota do front | `/membros` | `/pessoas` |
| Índice Elasticsearch | `membros` | `pessoas` (exige reindexação) |
| Evento | `exclusivo_membros` + `exclusivo_batizados` | **um só:** `exclusivo_membros` |
| Consolidado | `total, ativos, inativos, visitantes` | `total, membros, congregantes` |

### O toggle do evento colapsa

Hoje `exclusivoMembros` barra `VISITANTE` e `INATIVO`; `exclusivoBatizados` barra não batizado.
No modelo novo os dois viram a **mesma pergunta** — `vinculo == MEMBRO` — porque inativo não
existe (está arquivado, e arquivado já não aparece em lista nenhuma) e visitante virou
congregante. Some um toggle, some um campo do evento e some um ramo de validação.

### Filtros por vínculo

A separação membro/congregante foi o que mais confundiu na conversa com o pastor, então ela
precisa estar visível em **todo lugar onde o número aparece**:

- **Lista de pessoas** — botão "Filtros" que abre o painel (padrão do protótipo enviado), com
  Membros e Congregantes.
- **Relatórios financeiros** — filtrar contribuições por vínculo.
- **Consolidado de igrejas vinculadas** — cada congregação mostra seus números **separados**:
  quantos membros e quantos congregantes. Era o pedido explícito do autor.

## Riscos

**O maior risco não é técnico, é de omissão.** São 672 ocorrências de "membro" no backend e 69
arquivos no front; uma renomeação mecânica acerta a maioria e erra justamente onde a palavra
tinha outro sentido — comentários sobre "membro da igreja" (domínio, correto), nomes de variável
local, textos de interface, mensagens de erro. Cada camada precisa de revisão humana, não só de
busca e substituição.

**Segundo risco: a palavra "membro" continua existindo no domínio** — agora com o significado
estrito. Um `PessoaService` que fala de "membro" pode estar certo (referindo-se ao vínculo) ou
ser resíduo do rename. Só leitura resolve.

## Testes

- Um evento `exclusivo_membros` recusa `CONGREGANTE` e aceita `MEMBRO`.
- Contagem do consolidado separa membros e congregantes por igreja.
- Filtro por vínculo na lista de pessoas e no relatório financeiro.
- `ACESSO_COMUM` tem exatamente as permissões que `MEMBRO` tinha (nenhuma regressão de
  autorização) — inclusive o 403-vs-401 corrigido nesta sessão.
- Reindexação do Elasticsearch popula o índice `pessoas`.

## Fora de escopo

Tela de arquivados (Fase 3), histórico de mudança de vínculo (quando alguém é batizado e passa
de congregante a membro), e qualquer recorte além de membro/congregante — faixa etária e estado
civil continuam no BACKLOG.
