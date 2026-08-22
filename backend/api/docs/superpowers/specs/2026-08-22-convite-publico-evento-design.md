# Convite público de evento (Spec 2) — design

> Item 7 do `docs/BACKLOG-PRE-VENDA.md` (Spec D), Spec 2 do brainstorm de 2026-08-21/22.
> Continua direto da Spec 1 (`docs/superpowers/specs/2026-08-21-campos-personalizados-evento-design.md`,
> **feita e fechada**, commit `b0d05f5`) — reaproveita o schema de campos personalizados sem
> alterar sua estrutura básica, só estendendo `campo_personalizado_evento` com mapeamento.

## Motivação

A Spec 1 deixou o admin/líder montar um formulário extra por evento, mas só pra gente que **já
está no sistema** (Pessoa buscada pelo admin, ou a própria pessoa logada se auto-inscrevendo).
Não existe hoje um jeito de uma pessoa **de fora da igreja** se inscrever sozinha, nem de
compartilhar um evento por link (WhatsApp) pra alguém se inscrever sem passar pela recepção.

## Escopo desta spec

1. **Convite por link**: qualquer pessoa com inscrição no evento pode gerar um link e
   compartilhar (copiar/WhatsApp). Quem abre o link vê uma página pública bonita do evento e se
   inscreve — como pessoa conhecida (login) ou como convidada de quem compartilhou (sem conta).
2. **Modal unificado "Inscrever alguém"**: substitui os botões atuais "Inscrever membros" e
   "Vou levar alguém de fora" por um modal só, com 3 abas (Pessoas da igreja / Visitantes /
   Pessoa de fora), no padrão do cadastro de visitante em Célula.
3. **Mapeamento de campos personalizados**: um botão de template que adiciona de uma vez os
   campos de dado que o Domus já trata como estruturado em `Pessoa` (idade, estado civil, sexo,
   endereço) — e que, quando respondido por alguém que já tem esse dado no cadastro, **pula a
   pergunta** em vez de perguntar de novo.

## Fora do escopo desta spec

- Reescrever/reconverter automaticamente um convidado sem cadastro em `Visitante` ou `Pessoa`
  (segue manual, ferramentas que já existem).
- Formulário estruturado de endereço dentro de campo personalizado (fica texto livre).
- Mapeamento de campo personalizado feito manualmente pelo admin em campo criado do zero — só
  os campos vindos do template carregam mapeamento.
- Rastreio de "quem convidou" pra Pessoa cadastrada (decidido: não importa, ela é ela mesma no
  sistema, ganha inscrição normal).
- Diferenciar, na lista de inscritos, um convidado que entrou pelo link de um que a equipe
  cadastrou na mão — mostra só "Convidado de {nome}" pros dois casos.
- Mexer no fluxo antigo "Vou levar alguém de fora" (`ModalConvidado` / `AcompanhanteInscricao`)
  — continua existindo do jeito que está, sem tocar. Ver "Dois modelos coexistindo" abaixo.

---

## Decisões já tomadas (não rediscutir)

### Dois modelos coexistindo (ponto central desta spec)

O Domus já tinha, antes desta spec, um jeito de "levar alguém de fora": `AcompanhanteInscricao`
— nome + telefone texto livre, sempre pendurado na inscrição de alguém que **já está
inscrita**, sem formulário próprio, sem elegibilidade avaliada. Esse fluxo (`ModalConvidado`,
botão "Vou levar alguém de fora") **continua existindo exatamente como está** — não é tocado
por esta spec.

Esta spec introduz um **segundo modelo**, usado só pelas features novas (modal unificado, aba
Visitantes/Pessoa de fora, convite por link): a pessoa sem cadastro ganha sua **própria linha em
`inscricao_evento`** (não um acompanhante aninhado), com `pessoa_id` nulo, nome/telefone
guardados na própria linha, e uma referência opcional a quem convidou. Motivo de não reaproveitar
`AcompanhanteInscricao` aqui: essa spec precisa que "gente de fora" responda campos
personalizados como titular (não como dependente) e exista **mesmo que ninguém mais esteja
inscrita** — o modelo antigo não suporta nenhuma das duas coisas sem mudar sua natureza.

**Os dois modelos não se misturam.** `AcompanhanteInscricao` continua servindo só o caso
original (post-inscrição, +1 rápido, sem formulário). Todo o resto desta spec usa o modelo novo.
Se no futuro fizer sentido aposentar `AcompanhanteInscricao` em favor do modelo novo, é decisão
de outra spec — YAGNI aqui.

### Restante

- **Pessoa cadastrada que entra pelo link sempre ganha `InscricaoEvento` própria com
  `pessoa_id` preenchido** (elegibilidade avaliada normalmente, ocupa vaga como inscrita de
  verdade) — nunca vira "convidada sem cadastro", mesmo vindo por convite. Não guardamos "quem
  convidou" pra esse caso (`convidado_por_pessoa_id` fica nulo).
- **Convidado sem cadastro nunca é elegibilidade-checado** (mesmo comportamento que
  `AcompanhanteInscricao` já tem hoje: sem `Pessoa`, não tem `vinculo`/`sexo`/`estado_civil`/
  `data_nascimento` pra checar contra as restrições do evento). Segue bloqueado em evento
  `exclusivo_membros`, mesmo padrão de hoje (o botão nem aparece).
- **"Você também vai participar?"** é sempre perguntado quando quem está convidando/cadastrando
  ainda não tem inscrição no evento — **não bloqueia**: mesmo respondendo "não", a pessoa
  convidada ganha sua inscrição própria com `convidado_por_pessoa_id` apontando pra quem
  convidou; quem convidou **não** é inscrita nesse caso (só quem respondeu "sim" é inscrita).
- **Token do convite**: opaco, gerado aleatoriamente, guardado no Redis (mesmo padrão de
  `PasswordResetService`) — chave `convite:{token}` → `inscricaoId` de quem convidou. Sem
  tabela nova, sem JWT assinado. TTL calculado a partir da data do evento (expira alguns dias
  depois do evento acontecer — nunca fixo em minutos/horas).
- **`GET /convites/{token}` é público e enxuto**: devolve resumo do evento + nome/foto de quem
  convidou + campos personalizados do evento. **Nunca** devolve lista de inscritos nem qualquer
  outro dado de pessoa — é a defesa contra "vazar lista de nomes pra qualquer um com o link".
- **Mapeamento de campo personalizado nunca escreve de volta em `Pessoa`.** Resposta de campo
  mapeado é sempre snapshot isolado no evento, igual a qualquer resposta — só pula a pergunta
  quando o dado já existe, nunca sincroniza.
- **Campo "idade" mapeia pra `Pessoa.dataNascimento`, mas fica texto livre** (`tipo:
  TEXTO_CURTO`, rótulo "Idade", sem tipo `DATA` novo no enum). A pessoa digita a idade
  (número), não a data de nascimento — a resposta é um snapshot daquele momento, não precisa
  ser reaproveitável depois. "Pula pergunta" continua checando se `Pessoa.dataNascimento` já
  existe, mesmo o campo pedindo idade (nomes diferentes, mesmo dado por trás).
- **Endereço mapeia também** (skip-only): se `Pessoa.endereco` tem qualquer parte preenchida, a
  pergunta não aparece — sem tentar reconstruir o endereço em texto nem estruturar o campo.
- **Nome e telefone não entram no template de campos personalizados** — já são sempre coletados
  como identidade base de todo convidado; aparecem só na prévia do formulário como informação
  fixa, não como `CampoPersonalizadoEvento` de verdade.
- **3 abas no modal unificado** (Pessoas da igreja / Visitantes / Pessoa de fora), aba
  "Visitantes" com autocomplete pra não redigitar nome/telefone de quem a igreja já conhece.
- **"Compartilhar evento"** é uma ação própria (botão no card/drawer do evento) que abre o
  mesmo modal de compartilhamento (copiar link / WhatsApp) que a aba "Pessoa de fora" também
  abre — um modal de compartilhar, dois pontos de entrada.

---

## Modelo de dados

### Migration `V26__convite_evento.sql`

```sql
-- Convidado sem cadastro ganha inscrição própria (não mais acompanhante aninhado) — nome e
-- telefone vivem na própria linha quando pessoa_id é nulo POR ESTE MOTIVO. pessoa_id nulo já
-- tinha um significado diferente (Pessoa excluída via LGPD, ver V18) — a distinção entre os
-- dois casos é: convidado sempre tem nome_convidado preenchido; pessoa excluída, não.
ALTER TABLE inscricao_evento ADD COLUMN nome_convidado VARCHAR(255);
ALTER TABLE inscricao_evento ADD COLUMN telefone_convidado VARCHAR(20);

-- Referência informativa a quem convidou (Pessoa da igreja) — nula quando não há convidante
-- (cadastro avulso pela equipe, sem ninguém "trazendo"). Nunca usada pra Pessoa cadastrada que
-- entra por conta própria via link (decidido: não rastreamos isso pra quem já é do sistema).
ALTER TABLE inscricao_evento ADD COLUMN convidado_por_pessoa_id UUID REFERENCES pessoa(id);

CREATE INDEX idx_inscricao_convidado_por ON inscricao_evento (convidado_por_pessoa_id);

-- Mapeamento de campo personalizado pra dado estruturado de Pessoa (Spec 2, continua a Spec 1).
ALTER TABLE campo_personalizado_evento ADD COLUMN mapeamento VARCHAR(20);
COMMENT ON COLUMN campo_personalizado_evento.mapeamento IS
    'Marca campo vindo do template de dados básicos: IDADE | ESTADO_CIVIL | SEXO | ENDERECO. '
    'NULL = campo criado manualmente, nunca pula pergunta mesmo se a Pessoa já tiver o dado.';
```

- **Sem `CHECK` de banco** para "pessoa_id OU nome_convidado preenchido": a exclusão LGPD
  (`desvincularPessoa`, `UPDATE ... SET pessoa_id = NULL`) já produz linhas com os dois campos
  nulos, e um `CHECK` bloquearia esse `UPDATE`. A regra "toda inscrição nova precisa de pessoa
  OU nome_convidado" é só de aplicação (validada no service, nunca no banco) — mesmo padrão que
  `tipo`/`mapeamento` de `campo_personalizado_evento` já usam.
- `pessoa_id` já é nulável desde a V18 (exclusão LGPD) — não precisa de `ALTER` novo aqui.
- Sem `UNIQUE` novo: `uk_inscricao_evento_pessoa (evento_id, pessoa_id)` já existente não
  afeta linhas de convidado (`pessoa_id` nulo não colide, `NULL` nunca é igual a `NULL` em
  `UNIQUE` no Postgres) — várias pessoas de fora podem se inscrever no mesmo evento sem
  problema de unicidade.

### Distinção "convidado sem cadastro" × "pessoa excluída" (correção necessária no código atual)

Hoje, `InscritoResponse`/`ParticipanteResponse` mostram **sempre** "Pessoa removida do
sistema" quando `pessoa == null` (comentário em `InscritoResponse.java:30`, string em
`ParticipanteResponse.java:26`). Com o modelo novo, `pessoa == null` passa a significar duas
coisas diferentes:

| `pessoa_id` | `nome_convidado` | Significado |
|---|---|---|
| preenchido | — | Pessoa cadastrada, inscrição normal |
| nulo | preenchido | Convidado sem cadastro (modelo novo desta spec) |
| nulo | nulo | Pessoa excluída via LGPD (comportamento já existente) |

Os dois DTOs precisam mudar a regra pra: `pessoa != null ? pessoa.getNome() : (nomeConvidado !=
null ? nomeConvidado : "Pessoa removida do sistema")`. **Isto é uma correção obrigatória**, não
opcional — sem ela, todo convidado sem cadastro aparece na lista de inscritos como "Pessoa
removida do sistema", o que é o oposto do que a spec quer mostrar.

### Enum `MapeamentoCampoPersonalizado` (novo)

```java
package com.domus.api.modules.evento.campopersonalizado;

public enum MapeamentoCampoPersonalizado {
    IDADE,
    ESTADO_CIVIL,
    SEXO,
    ENDERECO
}
```

`CampoPersonalizadoEvento` ganha `@Enumerated(EnumType.STRING) private MapeamentoCampoPersonalizado mapeamento;`
(nullable).

### `InscricaoEvento` — campos novos

```java
@Column(name = "nome_convidado", length = 255)
private String nomeConvidado;

@Column(name = "telefone_convidado", length = 20)
private String telefoneConvidado;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "convidado_por_pessoa_id")
private Pessoa convidadoPor;

/** Vale pra Pessoa cadastrada OU convidado sem cadastro — nunca os dois nulos numa inscrição
 *  criada por este fluxo (checado em InscricaoService, não no banco — ver migration). */
public boolean isConvidadoSemCadastro() {
    return pessoa == null && nomeConvidado != null;
}
```

---

## Fluxo 1 — Convite por link

### Geração do token

`POST /eventos/{eventoId}/inscricoes/minha/convite` (autenticado)

- Resolve a inscrição do usuário logado nesse evento (`InscricaoRepository.findByEventoIdAndPessoaIdAndIgrejaId`
  — já existe, usado por `minhaInscricao`). Se não existir, `404` — o front garante que isso só
  é chamado depois de a pessoa confirmar "sim, também vou participar" e ter sido inscrita
  (`POST /eventos/{id}/inscricoes` primeiro, se necessário).
- Gera token aleatório (`SecureRandom`, mesmo padrão de `PasswordResetService.gerarToken()`),
  grava no Redis: `convite:{token}` → `inscricaoId` (string), TTL calculado a partir de
  `evento.fimEm` (ou `inicioEm` + margem, se `fimEm` for nulo).
- Resposta: `{ "token": "...", "link": "https://.../convite/{token}" }`.
- Não é idempotente: chamar de novo pra mesma inscrição gera um token novo — problema nenhum em
  ter mais de um token válido apontando pra mesma inscrição.

### Consulta pública

`GET /convites/{token}` (público, sem autenticação, fora do `SecurityFilter` de sessão)

- Resolve `inscricaoId` no Redis. Token não encontrado/expirado → `404` (`CONVITE_INVALIDO`).
- Carrega a inscrição (com `Evento`, `Igreja`, `Pessoa` do convidante). Inscrição cancelada, ou
  evento apagado (`deleted_at`) → mesmo `404` (`CONVITE_INVALIDO`), sem diferenciar motivo pro
  público.
- Evento já terminado (`fimEm` no passado, ou `inicioEm` no passado quando `fimEm` é nulo) →
  `410` (`EVENTO_ENCERRADO`).
- Resposta (`ConvitePublicoResponse`):
  ```
  eventoId, titulo, descricao, inicioEm, fimEm,
  localTexto | (localNome + endereco do LocalEvento, herdando endereço da igreja se aplicável),
  fotoId (banner do evento),
  igrejaNome, igrejaLogoFotoId,
  convidadoPorNome, convidadoPorFotoId,
  vagasRestantes (ou null se sem limite), preco (informativo),
  campos: List<CampoPersonalizadoResponse> (reaproveita o DTO da Spec 1)
  ```
- **Nunca** inclui lista de inscritos, e-mail/telefone de quem convidou, nem nada de outra
  pessoa além do convidante.

### Entrada — pessoa sem conta (convidado)

`POST /convites/{token}/entrar` (público)

```java
public record EntrarConviteRequest(
    @NotBlank @Size(max = 255) String nome,
    @Size(max = 20) String telefone,
    List<RespostaRequest> respostas   // mesmo DTO da Spec 1
) {}
```

- Resolve `inscricaoId` do convidante no Redis (mesma checagem de validade do `GET`).
- Cria uma **nova `InscricaoEvento`** (não um acompanhante): `pessoa = null`, `nomeConvidado`,
  `telefoneConvidado`, `convidadoPor = pessoa da inscrição resolvida do token`,
  `inscritoPorUsuarioId = null`. Reaproveita o lock pessimista de vagas que
  `InscricaoService.inscrever()` já usa (mesmo caminho de contagem — cada convidado é 1 vaga,
  igual a qualquer inscrito, sem soma separada de acompanhantes).
- Sem elegibilidade checada (decisão já tomada acima) — mas **bloqueado se
  `evento.exclusivoMembros`** (mesma trava que hoje esconde o botão de acompanhante).
- Depois de criar a inscrição, chama `CampoPersonalizadoService.responderComoConvidado(novaInscricaoId,
  respostas, igrejaId)` — nova sobrecarga sem checagem de dono/gestor (o token público *é* a
  autorização). Ver "Ajuste no `responder()`" abaixo. Note que aqui **não existe
  `acompanhanteId`** — o convidado é o próprio titular da sua `InscricaoEvento`, então a
  resposta vai com `acompanhante = null`, exatamente como o titular normal já faz na Spec 1.
- Vagas esgotadas → `409` (`VAGAS_ESGOTADAS`).
- Sucesso → `201` com o resumo do que foi inscrito (nome do convidado, evento).

### Entrada — pessoa com conta (login)

Não precisa de endpoint novo. O front, ao detectar sessão ativa (`GET /auth/me` com sucesso) ou
depois de login bem-sucedido a partir da página de convite:

1. Chama `GET /eventos/{eventoId}/inscricoes/minha` (já existe) pra saber se já está inscrita.
2. Se não está, mostra a confirmação e chama `POST /eventos/{eventoId}/inscricoes` (o
   `inscrever()` de sempre — elegibilidade, vagas, tudo já validado como qualquer
   auto-inscrição).
3. Se o evento tem campos personalizados, mostra o formulário (pulando os mapeados que a Pessoa
   já tem) e chama `PUT /inscricoes/{inscricaoId}/respostas` (já existe, dono responde por si).

Nenhuma referência a "veio de convite" é gravada — decidido que não importa pra Pessoa
(`convidadoPor` fica nulo nesse caminho).

### Ajuste no `responder()` (Spec 1) — autorização por convite

`CampoPersonalizadoService.responder` hoje só aceita "dono da inscrição" ou "quem gerencia
evento" (`pessoaLogadaId`/`role`). O caminho de convite público responde **pela própria
inscrição que acabou de criar**, sem usuário autenticado. Adiciono uma sobrecarga:

```java
/** Variante sem autor logado, usada só pelo fluxo de convite público (entrar sem conta): a
 *  posse do token — já validado antes de chegar aqui — É a autorização. Responde sempre como
 *  titular da inscrição recém-criada (acompanhanteId sempre null). */
@Transactional
public void responderComoConvidado(UUID inscricaoId, List<RespostaRequest> respostas, UUID igrejaId) {
    // mesma validação de obrigatoriedade/upsert do responder() original, sem checar
    // ehDono/podeGerenciarEventos.
}
```

`responder()` original passa a delegar pra um método privado compartilhado de validação +
upsert, chamado tanto por ele (depois de checar autorização) quanto por
`responderComoConvidado` (sem checar). Evita duplicar a lógica de obrigatoriedade.

### Mapeamento aplicado (comum aos dois caminhos)

Antes de montar a lista de campos pendentes pra mostrar no formulário (seja pra Pessoa logada,
seja pra o `GET /convites/{token}` decidir o que pedir), o backend filtra: se
`campo.mapeamento != null` **e** existe uma `Pessoa` no contexto (nunca existe pra convidado sem
cadastro) com aquele dado preenchido, o campo:

1. Não aparece no formulário (front nem pergunta).
2. Ganha uma resposta automática (snapshot) no momento da inscrição/resposta, pra manter a
   lista de inscritos consistente (admin vê a resposta igual, não precisa saber se veio do
   cadastro ou foi digitada).

```java
private Optional<String> valorJaConhecido(MapeamentoCampoPersonalizado mapeamento, Pessoa pessoa) {
    if (pessoa == null || mapeamento == null) return Optional.empty();
    return switch (mapeamento) {
        case IDADE -> Optional.ofNullable(pessoa.getDataNascimento())
                .map(d -> String.valueOf(Period.between(d, LocalDate.now()).getYears()));
        case ESTADO_CIVIL -> Optional.ofNullable(pessoa.getEstadoCivil()).map(Enum::name);
        case SEXO -> Optional.ofNullable(pessoa.getSexo()).map(Enum::name);
        case ENDERECO -> temAlgumDadoDeEndereco(pessoa.getEndereco())
                ? Optional.of(formatarEndereco(pessoa.getEndereco())) : Optional.empty();
    };
}
```

`listar(eventoId, igrejaId)` (Spec 1, tela de config) continua devolvendo todos os campos, sem
filtragem — o admin sempre vê e edita a lista completa. Uma assinatura nova,
`listarParaResponder(eventoId, igrejaId, pessoaOuNull)`, devolve só os campos que ainda
precisam de resposta (usada pelo formulário de resposta e por `GET /convites/{token}`).

---

## Fluxo 2 — Modal unificado "Inscrever alguém"

Substitui os botões "Inscrever membros" e "Vou levar alguém de fora" em
`DrawerDetalheEvento.tsx` (e no equivalente da tela inicial, se houver componente próprio — a
verificar na implementação; mesmo componente reaproveitado).

> **Atenção**: "Vou levar alguém de fora" (`ModalConvidado`) some da UI como opção separada —
> vira parte da aba "Pessoa de fora" do modal novo. O componente `ModalConvidado` e o endpoint
> `POST .../acompanhantes` **continuam existindo no backend** (não removidos, sem breaking
> change em dado antigo), só deixam de ser alcançáveis por um botão próprio na tela de evento.
> Se isso for indesejado — por exemplo, se você preferir manter os dois botões visíveis, um pro
> modelo antigo (mais rápido, +1 sem formulário) e outro pro novo (com formulário/link) — é uma
> decisão de UI a validar durante a implementação do frontend, não trava o backend.

### Abas

1. **Pessoas da igreja** — é o `ModalInscreverPessoas` de hoje (busca `Pessoa`, `POST
   /eventos/{eventoId}/inscricoes/pessoas`), sem mudança de comportamento — só passa a viver
   dentro de uma aba em vez de modal próprio.
2. **Visitantes** — busca `Visitante` por nome (endpoint leve novo, ver abaixo). Ao selecionar,
   pré-preenche nome/telefone na mesma UI da aba 3.
3. **Pessoa de fora** — nome + telefone (nunca pré-preenchido), mais os campos personalizados
   do evento (se houver — mapeamento nunca pula aqui, não existe `Pessoa` pra checar).

Abas 2 e 3 convergem no mesmo destino: criar uma `InscricaoEvento` com `pessoa = null`,
`nomeConvidado`/`telefoneConvidado`, `convidadoPor = pessoa de quem está preenchendo o modal`
(novo endpoint, ver abaixo) + responder campos personalizados se houver.

### "Você também vai participar?"

Se quem está preenchendo o modal (a pessoa logada) ainda não tem inscrição no evento, ao
confirmar qualquer ação das abas 2/3 (ou "compartilhar link"), aparece a pergunta antes de
prosseguir:

- **"Sim"** → cria a inscrição própria primeiro (`POST /eventos/{eventoId}/inscricoes`), depois
  segue o fluxo normal (convidado ganha `convidadoPor` apontando pra essa Pessoa).
- **"Não"** → **não inscreve quem está convidando** — só cria a inscrição do convidado, com
  `convidadoPor` apontando mesmo assim pra Pessoa de quem convidou (referência informativa, não
  gera inscrição pra ela).

### Endpoint novo — criar convidado sem cadastro

`POST /eventos/{eventoId}/inscricoes/convidados` (autenticado — qualquer perfil que possa abrir
o modal de inscrever)

```java
public record CriarConvidadoRequest(
    @NotBlank @Size(max = 255) String nome,
    @Size(max = 20) String telefone,
    List<RespostaRequest> respostas
) {}
```

- `convidadoPor` = Pessoa do usuário autenticado (`UsuarioAutenticado.getPessoaId()`).
- Mesma trava de `evento.exclusivoMembros` e mesma checagem de vagas do fluxo de convite por
  link (reaproveita o método de serviço comum — ver abaixo).
- Reaproveitado tanto pelas abas 2/3 do modal (equipe/membro cadastrando na hora) quanto,
  internamente, pelo `POST /convites/{token}/entrar` (que só resolve quem é `convidadoPor` de
  um jeito diferente — via token em vez de sessão).

`InscricaoService` ganha um método comum:

```java
/** Cria a inscrição de alguém sem cadastro, ocupando vaga, sem elegibilidade checada. Usado
 *  pelo modal presencial (convidadoPorPessoaId = quem está logado) e pelo convite público
 *  (convidadoPorPessoaId = dono do token). */
@Transactional
public InscricaoEvento inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                           String telefone, UUID convidadoPorPessoaId) { ... }
```

### Endpoint novo — busca leve de Visitante

`GET /visitantes/busca-leve?q=` (autenticado, qualquer perfil que possa abrir o modal de
inscrever) — devolve só `{ id, nome, telefone }`, paginação simples, mesmo padrão de
`usePessoas`/busca de pessoa já usado no modal atual. Não reaproveita o endpoint completo de
`Visitante` (que traz todos os campos de acompanhamento de célula — dado que não tem nada a ver
com inscrição de evento e não deveria vazar pra quem só gerencia evento, não célula).

### Compartilhar evento

Botão "Compartilhar" no card/drawer do evento (visível sempre que `requerInscricao` e a
situação permite inscrição) e dentro da aba "Pessoa de fora" — os dois abrem o mesmo
`ModalCompartilharConvite`:

- Se a pessoa logada ainda não está inscrita → pergunta "você também vai participar?" primeiro
  (mesmo padrão acima, sem bloquear).
- Gera o token (`POST .../convite`), mostra o link com botão "Copiar link" e "Enviar por
  WhatsApp" (`https://wa.me/?text=...` com o link — não integra API do WhatsApp, é só o link
  `wa.me`).
- Texto fixo no modal: "Quem usar este link entra como seu convidado."

---

## Fluxo 3 — Template de campos personalizados

No painel de configuração (`EventoForm.tsx`, seção "Campos personalizados", já existe da Spec
1), acima da lista, um botão **"Usar template de dados básicos"**:

- Ao clicar, adiciona de uma vez 4 campos à lista (só no estado local do formulário, igual
  qualquer edição da lista hoje):
  - **"Idade"** — `tipo: TEXTO_CURTO`, placeholder "Ex.: 24", `mapeamento: IDADE`.
  - **"Estado civil"** — `tipo: OPCAO_UNICA`, opções `Solteiro(a) | Casado(a) | Divorciado(a) |
    Viúvo(a)` (mesmos valores de `EstadoCivil`, rotulados em português), `mapeamento:
    ESTADO_CIVIL`.
  - **"Sexo"** — `tipo: OPCAO_UNICA`, opções `Homem | Mulher` (mesmos valores de `Sexo`),
    `mapeamento: SEXO`.
  - **"Endereço"** — `tipo: TEXTO_CURTO`, placeholder "Rua, número, bairro, cidade",
    `mapeamento: ENDERECO`.
- Admin pode apagar qualquer um dos 4 (lista editável de sempre), reordenar, mudar o rótulo. Só
  não pode mudar o `tipo`/opções de um campo mapeado sem perder o mapeamento — mudar qualquer
  coisa estrutural desmarca `mapeamento = null` (explicação simples: "se você mexeu na
  estrutura, não é mais o campo padrão").
- Aviso fixo no topo do painel: "Estes campos também aparecem para quem se inscreve sem
  cadastro no sistema (convidados) — pense no que você gostaria de saber dessas pessoas."
- Prévia ao vivo (já existe da Spec 1) ganha, no topo, nome e telefone como linhas fixas/
  não-editáveis com a legenda "sempre coletado automaticamente".

---

## Frontend — página pública `/convite/[token]`

Rota nova fora do grupo `(app)` (mesmo nível de `/login`, `/termos`), layout público próprio
(sem sidebar/navbar autenticada). Landing page de convite, no espírito do segundo protótipo do
Stitch:

- **Topo**: logo + nome da igreja.
- **Hero**: banner do evento (`fotoId`) em destaque, título sobreposto.
- **Card "Você foi convidado por"**: foto (se tiver) + nome de quem convidou.
- **Descrição** do evento.
- **Card de info**: data/horário, local (endereço), vagas restantes se aplicável.
- **Botão "Inscrever-se"** — CTA único e visível. Só ao clicar é que abre a decisão:
  - Chama `GET /auth/me` (em paralelo ao carregar a página, ou na hora do clique).
  - Tem sessão → abre o formulário de campos pendentes (mapeamento já resolvido) e confirma
    inscrição própria.
  - Sem sessão → abre duas opções: "Já tenho conta" (vai pro `/login`, com redirect de volta
    pra este link salvo) **ou** "Continuar sem conta" (formulário nome+telefone+campos, `POST
    /convites/{token}/entrar`).
- Estados de erro: convite inválido/expirado (`404`) e evento encerrado (`410`) — telas
  simples, sem navegação de app (quem chega aqui pode nunca ter usado o Domus).

---

## Segurança

- `GET /convites/{token}` e `POST /convites/{token}/entrar` ficam fora do `SecurityFilter` de
  sessão (rota pública, mesmo tratamento que `/auth/login`, `/auth/forgot-password`) — mas
  dentro do `RateLimitFilter`, na faixa mais restritiva (mesma de `/auth/**`, não a global de
  100/min).
- Token não é adivinhável (`SecureRandom`, mesmo gerador de `PasswordResetService`) — sem
  enumeração de convites válidos por força bruta dentro de qualquer janela razoável.
- CSRF: não se aplica (sem cookie de sessão nesses dois endpoints), mesma exceção que login já
  tem no `SecurityConfig`.
- `GET /convites/{token}` nunca devolve e-mail/telefone de quem convidou, nem lista de
  inscritos, nem qualquer outra `Pessoa` além do nome/foto do convidante.
- Vagas: `inscreverConvidado()` usa o mesmo lock pessimista que `inscrever()` já usa — sem
  caminho novo de corrida (a inscrição de convidado é uma `InscricaoEvento` como outra
  qualquer, entra na mesma contagem).

---

## Testes planejados (visão geral)

> Cenários nomeados aqui pra não perder o fio — a implementação segue TDD (teste escrito antes
> do código de produção). O plano de implementação detalha cada um, com Mockito puro pra
> service (padrão do projeto) e `@DataJpaTest`/`@SpringBootTest` só onde justificado.

### `InscricaoService.inscreverConvidado` (novo)

- `criaInscricaoComPessoaNulaENomeConvidadoPreenchido`.
- `ocupaVagaComoQualquerInscricao` — contagem de vagas considera a nova linha.
- `recusaQuandoVagasEsgotadas` — `VAGAS_ESGOTADAS`, mesmo código de sempre.
- `recusaQuandoEventoExclusivoMembros`.
- `naoChecaElegibilidade` — evento com restrição de idade/sexo/estado civil não bloqueia
  convidado sem cadastro (prova negativa: nenhuma das quatro regras de `ElegibilidadeService` é
  chamada pra esse caminho).
- `convidadoPorPessoaIdEhGravadoQuandoInformado`.
- `convidadoPorPessoaIdFicaNuloQuandoCadastroAvulsoSemHost` (recepção cadastrando alguém sem
  ninguém "trazendo" — se esse caso continuar existindo depois de fechar a pergunta em aberto
  sobre titular avulso).

### `InscritoResponse`/`ParticipanteResponse` (correção de exibição)

- `mostraNomeConvidadoQuandoPessoaNulaENomeConvidadoPreenchido`.
- `mostraPessoaRemovidaQuandoPessoaENomeConvidadoAmbosNulos` — prova que o comportamento
  antigo (LGPD) não quebrou.
- `mostraNomeDaPessoaQuandoPessoaPreenchida` — caminho normal, sem regressão.

### `CampoPersonalizadoService` (extensão)

- `campoMapeadoPulaQuandoPessoaJaTemODado` — Pessoa com `dataNascimento` preenchida não recebe
  o campo "Idade" mapeado na lista de pendentes.
- `campoMapeadoApareceQuandoPessoaNaoTemODado` — Pessoa sem `estadoCivil` recebe o campo
  normalmente.
- `campoMapeadoSempreApareceParaConvidadoSemCadastro` — sem `Pessoa` no contexto, todos os
  campos mapeados aparecem, nunca pulam.
- `respostaAutomaticaEhCriadaQuandoCampoPulado` — ao confirmar inscrição/resposta, um campo
  mapeado pulado ainda gera uma `RespostaCampoPersonalizado` com o valor vindo de `Pessoa`
  (snapshot), `acompanhante = null`.
- `idadeMapeadaCalculaAPartirDeDataNascimento` — valor automático é a idade em anos, não a data.
- `campoEditadoEstruturalmentePerdeMapeamento` — mudar `tipo`/opções de um campo mapeado zera
  `mapeamento`.
- `enderecoMapeadoPulaSeQualquerParteDoEnderecoExiste` — Pessoa só com `cidade` preenchida (sem
  CEP) já é suficiente pra pular o campo Endereço.
- `responderComoConvidadoNaoExigeDonoNemGestor` — a nova sobrecarga aceita responder sem checar
  `pessoaLogadaId`/`role`.
- `responderComoConvidadoAindaValidaObrigatoriedade` — mesmo sem autor logado, campo
  obrigatório sem resposta continua lançando `BusinessException`.

### `ConviteService` (novo)

- `gerarTokenGravaNoRedisComTtlAteFimDoEvento`.
- `gerarTokenUsaInicioMaisMargemQuandoFimEmEhNulo`.
- `gerarTokenLancaNotFoundSeInscricaoNaoPertenceAIgreja`.
- `resolverTokenDevolveInscricaoQuandoValido`.
- `resolverTokenLancaInvalidoQuandoTokenNaoExisteOuExpirou`.
- `resolverTokenLancaEncerradoQuandoEventoJaAconteceu` — `410`.
- `resolverTokenLancaInvalidoQuandoInscricaoDoConvidanteFoiCancelada`.

### Controllers (integração, `@SpringBootTest`)

- `gerarConviteExigeAutenticacao` — `401` sem cookie de sessão.
- `getConvitePublicoNaoExigeAutenticacao` — `200` sem cookie.
- `getConvitePublicoNaoVazaEmailOuTelefoneDoConvidante` — prova negativa no payload.
- `entrarComoConvidadoCriaInscricaoEOcupaVaga`.
- `entrarComoConvidadoRecusaQuandoVagasEsgotadas` — `409`.
- `entrarComoConvidadoRespeitaCamposObrigatorios` — `422`.
- `buscaLeveDeVisitanteDevolveApenasIdNomeTelefone` — prova negativa: sem `observacoes`,
  `contatoRealizado` etc.
- `buscaLeveDeVisitanteRespeitaIsolamentoPorIgreja`.

### Frontend (validação manual no navegador — sem Jest/Playwright configurado no projeto)

- Página `/convite/[token]`: convite válido, expirado, evento encerrado, com/sem sessão ativa,
  mobile.
- Modal unificado: as 3 abas, "você também vai participar?" (sim e não), compartilhar (copiar +
  WhatsApp).
- Builder de campos: template adiciona os 4, apagar um funciona, editar estrutura de um
  mapeado avisa que perde o mapeamento, prévia mostra nome/telefone fixos.
- Lista de inscritos: convidado sem cadastro aparece com nome certo (não "Pessoa removida do
  sistema"), selo "Convidado de {nome}".

---

## `convidado_por_pessoa_id` — sempre preenchido

Toda inscrição de convidado criada pelo modal presencial tem `convidadoPor` = quem está logado
fazendo o cadastro, **mesmo em cadastro avulso** (visitante que chegou sozinho, sem ninguém da
igreja "trazendo" de fato). O campo cumpre dois papéis ao mesmo tempo: indica convite quando
houve um de verdade, e serve de **auditoria** (quem foi responsável por lançar aquele registro)
quando não houve — mesmo raciocínio de `criado_por_usuario_id` que já existe em outras
entidades do sistema. Simplifica a UI: nunca precisa perguntar/distinguir "estou trazendo
alguém" de "só estou cadastrando um visitante avulso" — o campo sempre é preenchido com quem
está logado.
