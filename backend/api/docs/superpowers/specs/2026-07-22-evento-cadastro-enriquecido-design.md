# Cadastro de evento enriquecido + elegibilidade — design

**Data:** 2026-07-22
**Fase do roadmap:** 2
**Antecessoras:** Spec A (inscrição em evento, 2026-07-21), upload de foto (2026-07-22)
**Corresponde a:** "Spec B" e "Elegibilidade por perfil" do BACKLOG (linhas 285–340)

## Problema

O cadastro de evento é o formulário mais pobre do Domus. Ele sabe *quando* e *o quê*, mas não
sabe **onde** (texto livre sem estrutura), **de quem é** (não há responsável), **que espécie de
evento é** (não há tipo) nem **para quem é** (só existe o `exclusivoMembros`, um booleano).

Três consequências concretas:

1. O mesmo "Santuário Principal" é redigitado em todo evento, com grafias diferentes, e o
   endereço da igreja — que já está cadastrado — nunca aparece no detalhe do evento.
2. Não há como filtrar ou agrupar eventos: sem tipo, a lista é uma pilha cronológica.
3. Um retiro de jovens aceita inscrição de qualquer pessoa. A igreja controla isso no olho,
   e o sistema não ajuda.

A restrição por perfil (`exclusivoMembros`) existe e prova que a necessidade é real — ela só
foi construída para um caso e não generaliza.

## Escopo

Entra: local como entidade, tipo, responsável, banner, layout de duas colunas, auditoria de
evento, e elegibilidade completa (faixa etária, vínculo, estado civil, sexo).

Fica de fora: recorrência (Spec C), campos personalizados por inscrito (Spec D), programação e
equipe servindo (Spec E). Cobrança continua fora — preço segue informativo (decisão da Fase 6).

---

## Decisões

### 1. Local: tabela **e** texto livre

O backlog pedia duas coisas em tensão — capacidade por local (exige registro) e locais ad-hoc
(exige texto). Ambas são reais: o "Salão Social" se repete o ano inteiro; a "chácara do João"
acontece uma vez.

**`local_evento`** guarda os locais reusáveis: nome, capacidade e endereço próprio. O evento
aponta para um deles (`local_id`) **ou** guarda um texto (`local_texto`) — nunca os dois, com
`CHECK` no banco garantindo.

A capacidade **sugere** as vagas; não as impõe. Cabem 300 no santuário, mas o retiro pode
limitar em 80 por causa do ônibus. Sugestão que sobrescreve escolha do usuário vira armadilha,
então ela só preenche o campo **quando ele está vazio**.

**Endereço nulo herda o da igreja.** O "Santuário Principal" não tem endereço próprio — ele *é*
o endereço da igreja, já cadastrado. Redigitá-lo criaria duas fontes da verdade que divergem no
dia em que a igreja se muda. A "Chácara Betel" tem o seu.

**Migração:** o `evento.local` (VARCHAR) atual vira `local_texto`. Nenhum evento existente
quebra e ninguém precisa cadastrar nada para o sistema continuar funcionando. Os locais viram
entidade por adoção, no ritmo da igreja.

### 2. Tipo: texto livre que aprende, sem cadastro prévio

Decisão do autor, contra a alternativa de uma tabela com CRUD: **obrigar a cadastrar antes de
usar é atrito num campo preenchido de passagem**.

O campo combina duas fontes de sugestão:

| Fonte | Origem | Papel |
|---|---|---|
| Sementes | constante no código (Culto, Conferência, Retiro, Ensaio, Reunião) | a igreja não encara campo vazio no primeiro evento |
| Aprendidas | `SELECT DISTINCT tipo` da própria igreja | o vocabulário real ("Vigília", "Santa Ceia", "EBD") |

**A ordem é o que faz parecer que aprende:** primeiro o que a igreja mais usa (por frequência),
depois as sementes ainda não usadas. Com o tempo, o que a igreja digitou uma vez passa na frente
do que o sistema chutou e ninguém usou.

**Normalização ao salvar** (`trim`, comparação sem acento e sem caixa) é o que impede a lista de
sugestões de virar o lixo que o não-cadastro poderia causar: quem digita "culto" onde já existe
"Culto" reaproveita o existente em vez de criar um gêmeo.

⚠️ **Não chamar de "categoria".** O nome já significa outra coisa no Domus
(`categoria_financeira`) e a ambiguidade contaminaria toda conversa futura.

O mesmo componente serve ao **local ad-hoc**: a "chácara do João" digitada hoje vira sugestão no
próximo evento.

### 3. Elegibilidade: quatro restrições, um mecanismo

Quatro recortes, todos opcionais e combináveis:

| Restrição | Lê de | Situação |
|---|---|---|
| Faixa etária | `pessoa.data_nascimento` | nova |
| Vínculo (só batizados) | `pessoa.vinculo` | **já existe** como `exclusivoMembros` |
| Estado civil | `pessoa.estado_civil` | nova |
| Sexo | `pessoa.sexo` | nova — **o campo não existe ainda** |

`exclusivoMembros` é **absorvido**, não duplicado: continua sendo a coluna que é, e passa a ser
lido pelo mesmo mecanismo das demais. Um segundo caminho fazendo a mesma coisa é como as regras
divergem.

**Faixa etária = recorte nomeado + idades ajustáveis.** O nome (Kids, Adolescentes, Jovens,
Adultos, 3ª idade) alimenta o selo no card e o filtro; as idades alimentam a validação. Vem
preenchido com um padrão e a igreja ajusta — porque a idade em que alguém deixa de ser "jovem"
varia entre igrejas, e um sistema que decide isso sozinho está errado em metade delas.

**`pessoa.sexo`** entra como `HOMEM` | `MULHER`, nulável. Nulável porque as pessoas já
cadastradas não têm valor, e escolher um padrão seria inventar dado sobre gente real.

### 4. Sem o dado, bloqueia — com mensagem que ensina o caminho

`data_nascimento`, `estado_civil` e `sexo` são todos opcionais. Quem não tem o campo preenchido
**não passa** na restrição que depende dele.

O risco, registrado no backlog: *"um filtro que barra em silêncio quem não tem o campo
preenchido vira suporte"*. O silêncio é o problema, não o bloqueio. Por isso o impedimento tem
**código próprio** (`SEM_DATA_NASCIMENTO`, não `FAIXA_ETARIA`) e mensagem que diz o que fazer —
*"seu cadastro não tem data de nascimento; procure a secretaria da igreja"*, não "você não
pode".

### 5. Quem gerencia pode contornar; vaga inexistente ninguém contorna

Caso real: o líder de 34 anos que serve no retiro de jovens. Também o preletor, o motorista, a
cozinha. Uma regra que barra o organizador do próprio evento é a primeira que a igreja pede para
desligar — e desligá-la a derruba para todo mundo.

- **Auto-inscrição respeita a regra sempre.** Sem exceção.
- **Admin/líder inscrevendo outra pessoa** recebe o aviso ("Maria tem 34 anos e este evento é
  para 18–29") e decide, enviando `confirmado=true`.

**Nem todo impedimento é contornável.** `VAGAS_ESGOTADAS` não é: vaga que não existe não vira
exceção administrativa. O `Impedimento` carrega essa distinção como dado (`contornavel`), não
como regra espalhada por quem consome.

---

## Modelo de dados (migration V3)

```
local_evento
  id            UUID PK
  igreja_id     UUID FK NOT NULL      -- isolamento multi-tenant
  nome          VARCHAR NOT NULL
  capacidade    INTEGER NULL          -- NULL = não declarada; sugere vagas
  cep_logradouro_numero          VARCHAR NULL   -- NULL = herda o da igreja
  complemento_bairro_cidade_uf   VARCHAR NULL
  deleted_at    TIMESTAMP             -- soft delete, como toda entidade
  UNIQUE (igreja_id, nome) onde deleted_at IS NULL
```

```
evento  (colunas novas)
  local_id                    UUID FK NULL -> local_evento(id) ON DELETE SET NULL
  local_texto                 VARCHAR NULL
  tipo                        VARCHAR NULL
  responsavel_pessoa_id       UUID FK NULL -> pessoa(id) ON DELETE SET NULL
  criado_por_usuario_id       UUID FK NULL -> usuario(id)
  atualizado_por_usuario_id   UUID FK NULL -> usuario(id)
  recorte_etario              VARCHAR NULL   -- nome do recorte; NULL = sem restrição de idade
  idade_min                   INTEGER NULL
  idade_max                   INTEGER NULL
  restricao_estado_civil      VARCHAR NULL   -- SOLTEIRO | CASADO
  restricao_sexo              VARCHAR NULL   -- HOMEM | MULHER

  CHECK (local_id IS NULL OR local_texto IS NULL)   -- nunca os dois
  CHECK (idade_min IS NULL OR idade_max IS NULL OR idade_min <= idade_max)
```

```
pessoa  (coluna nova)
  sexo   VARCHAR NULL   -- HOMEM | MULHER
  CHECK (sexo IN ('HOMEM','MULHER'))
```

`evento.local` (VARCHAR) é **renomeado** para `local_texto` — os dados existentes seguem
válidos, nenhum evento quebra.

**Por que colunas e não uma tabela `restricao_evento`:** o `CLAUDE.md` já fixou a regra —
*"tabela nova é para N-para-N ou dado repetido; não para 1-para-1"*. Cada evento tem no máximo
um conjunto de restrições, logo elas são atributos dele.

`ON DELETE SET NULL` no responsável e no local: arquivar a pessoa que organizava, ou o local que
foi desativado, **não pode apagar o evento** nem impedir a operação. O evento perde a referência
e continua existindo.

---

## Avaliação da elegibilidade

### A forma errada, e por que é errada

Uma escada de `if` num método só funciona e apodrece: cada restrição nova edita o mesmo método;
a primeira falha aborta e **esconde as outras** (a pessoa corrige uma coisa e descobre a
seguinte); e a escada acaba repetida no front para desabilitar o botão.

Isso contraria o guardrail do `CLAUDE.md`: *"estenda sem editar"*.

### A forma adotada

```java
public interface RegraElegibilidade {
    /** Vazio = aprovado. Preenchido = por que não pode. */
    Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa);
}

public record Impedimento(String codigo, String mensagem, boolean contornavel) {}

public record Elegibilidade(boolean apto, List<Impedimento> impedimentos) {}
```

Cinco implementações independentes — `RegraFaixaEtaria`, `RegraVinculo`, `RegraEstadoCivil`,
`RegraSexo`, `RegraVagas` — que o Spring injeta como `List<RegraElegibilidade>`. **Adicionar a
sexta cria um arquivo e não edita nenhum.**

O `ElegibilidadeService` roda **todas** e acumula. Avaliar tudo em vez de parar na primeira é
deliberado: a pessoa vê de uma vez tudo o que a impede, em vez de descobrir por tentativa e
erro.

O `codigo` existe para o front decidir sem interpretar texto de mensagem. O `contornavel`
carrega a distinção da decisão 5.

### ⚠️ `RegraVagas` NÃO substitui a contagem com lock

A Spec A já conta vagas **dentro da transação, com lock pessimista** — é o que impede duas
inscrições simultâneas de ocuparem a mesma última vaga. Essa contagem continua sendo **a única
autoridade** sobre vaga.

A `RegraVagas` aqui existe só para o `GET /elegibilidade` conseguir dizer "esgotado" antes de a
pessoa tentar. Ela **lê sem lock** e, por isso, pode estar desatualizada por milissegundos — o
que é aceitável para pintar uma tela e inaceitável para decidir uma inscrição.

Concretamente: no `POST`, quem decide sobre vaga é a contagem travada da Spec A, não esta regra.
Se as duas discordarem, a travada vence. Duplicar a contagem autoritativa aqui reintroduziria a
corrida que a Spec A fechou.

### Onde é aplicada

| Endpoint | Papel |
|---|---|
| `POST /eventos/{id}/inscricoes` | **a validação real**; 422 + lista de impedimentos |
| `GET /eventos/{id}/elegibilidade` | só para a tela saber o que mostrar antes de tentar |

O segundo existe para a UX e **nunca como defesa** — é o *"esconder no front não é esconder"* do
`CLAUDE.md`. Chamar o `POST` direto pelo Insomnia esbarra na mesma regra.

O parâmetro `confirmado=true` só derruba impedimentos com `contornavel = true`, e só para quem
passa em `podeGerenciarInscricoes`. Enviado por quem não tem a permissão, é ignorado.

---

## Frontend

### Layout de duas colunas

Já existe no `PessoaForm` (`colunas` / `colunaEsquerda` / `colunaDireita`) — é alinhamento a um
padrão do projeto, não padrão novo.

O critério de divisão tem consequência prática: **o campo raro para de atrapalhar o campo
comum.**

```
ESQUERDA (o que é o evento)        DIREITA (como é administrado)
  Título                             Banner (UploadFoto)
  Tipo        [chips + digitar]      Responsável [busca em pessoas]
  Descrição                          ── Inscrições ──
  Data e horário                     Requer inscrição [toggle]
  Local       [select ou livre]      Vagas · Preço
    └ capacidade sugere vagas        Para quem é [recortes]
```

**Mobile:** as colunas colapsam para uma, nesta ordem. Obrigatório por convenção do projeto, não
etapa separada.

### Componentes

- **`<InputComSugestoes>`** (novo, genérico): chips + digitação livre, sugestões vindas do
  servidor. Serve ao tipo do evento e ao local ad-hoc.
- **Seletor de local:** `<select>` com os cadastrados (exibindo capacidade) mais `— outro local —`,
  que troca o select por texto livre.
- **Responsável:** busca por nome; reusa o padrão do `ModalInscreverPessoas`.
- **"Para quem é":** recolhido em "Todos" por padrão. Um evento comum não deve pagar o preço
  visual de uma feature que a maioria dos eventos não usa.

### Onde o resultado aparece

- **Selo no card:** o nome do recorte ("Jovens", "Kids"). Sai de graça porque o recorte tem nome.
- **Filtro na lista:** por tipo e por recorte.
- **No detalhe:** responsável, endereço do local (herdado ou próprio), e "criado por / atualizado
  por".
- **No modal de inscrição:** os impedimentos, e o "inscrever mesmo assim" para quem gerencia.

### Botão desabilitado, nunca escondido

Quem não é elegível vê o botão **visível, desabilitado, com o motivo ao lado**. Botão que some
deixa a pessoa achando que o sistema quebrou; botão desabilitado com "este evento é para 18 a 29
anos" ensina a regra.

---

## Riscos

**O maior: a validação de elegibilidade barrar quem deveria entrar.** Todo campo que ela lê é
nulável, e a igreja tem cadastros incompletos hoje. Mitigação: código de impedimento específico
para dado ausente, mensagem que diz o que fazer, e o contorno por quem gerencia. Precisa de
teste para cada restrição **com o campo nulo**, não só com valor fora da faixa.

**Segundo: a normalização do tipo agrupar o que não devia.** Comparar sem acento e sem caixa faz
"Culto" e "culto" convergirem — desejado. Precisa de teste provando que não colapsa tipos
genuinamente distintos.

**Terceiro: a sugestão de vagas sobrescrever a escolha do usuário.** Só preenche campo vazio.
Teste explícito.

**Quarto: `local_id` e `local_texto` preenchidos juntos.** O `CHECK` protege no banco; o service
não deve depender só dele para dar mensagem decente.

---

## Testes

- Cada uma das quatro restrições: aprova dentro, reprova fora, **e reprova com código próprio
  quando o campo é nulo**.
- Impedimentos são acumulados, não interrompidos na primeira falha.
- `confirmado=true` derruba impedimento contornável; **não** derruba `VAGAS_ESGOTADAS`.
- `confirmado=true` enviado por quem não gerencia é ignorado.
- Auto-inscrição nunca contorna, mesmo se a pessoa gerencia.
- `GET /elegibilidade` e `POST /inscricoes` concordam sobre a mesma pessoa e evento.
- **A contagem de vagas com lock da Spec A continua sendo a autoridade:** o teste de
  concorrência existente (duas inscrições simultâneas na última vaga) segue passando depois
  desta spec. Se ele quebrar, `RegraVagas` invadiu o caminho do `POST`.
- Local de outra igreja não pode ser vinculado a um evento (isolamento multi-tenant).
- `CHECK` recusa `local_id` e `local_texto` juntos.
- Capacidade sugere vagas só quando o campo está vazio.
- Normalização do tipo: "culto", "Culto " e "CULTO" convergem; "Culto" e "Cultinho" não.
- Arquivar a pessoa responsável não apaga nem quebra o evento.
- Evento existente (com `local` texto) segue íntegro após a migration.

## Fora de escopo

Recorrência (Spec C), campos personalizados por inscrito (Spec D), programação e equipe servindo
(Spec E), cobrança real (Fase 6), lista de espera, e capacidade do local **impondo** limite de
vagas.
