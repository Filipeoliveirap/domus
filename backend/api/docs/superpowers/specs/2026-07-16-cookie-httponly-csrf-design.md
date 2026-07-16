# Design — Sessão em cookie `httpOnly` + reativação do CSRF

- **Data:** 2026-07-16
- **Fase:** 1 (fundações, autenticação e endurecimento de produção)
- **Status:** design aprovado, pendente plano de implementação

## Problema

Hoje o front guarda **os dois** tokens (access e refresh) no `localStorage`, de duas
formas simultâneas:

- `localStorage.setItem('domus:token', ...)` (manual, em `authStore.ts`);
- middleware `persist` do zustand (chave `domus:auth`, **sem `partialize`** — persiste o
  estado inteiro, incluindo `refreshToken`, `id`, `role`).

Além disso há um `document.cookie = 'domus:token=...'` setado **por JavaScript**, sem
`httpOnly`, `Secure` ou `SameSite`.

Consequência: **qualquer XSS lê tudo e rouba a sessão inteira** — inclusive o refresh
token, que vale 7 dias de sessões novas. É o maior gap de segurança aberto da Fase 1, e o
princípio do roadmap é "fundações e segurança antes de dado real".

### Bug latente descoberto no diagnóstico

O JWT dura **10 minutos**, mas o cookie `domus:token` é setado com `max-age` de **7 dias**.
Os dois mentem um pro outro. Passa despercebido porque o `src/proxy.ts` (middleware do
Next 16) só checa a **existência** do cookie — nunca valida. Ou seja, `proxy.ts` **não
entrega segurança nenhuma**: `document.cookie = 'domus:token=banana'` passa por ele. Ele é
conforto visual (evitar piscar tela), não porteiro. O porteiro sempre foi o backend.

## Decisão de arquitetura: proxy no Next (`/api/*` → backend)

Cookie é regido por **site** (domínio registrável), não por **origem**. Porta e subdomínio
não contam. Em dev, `localhost:3000` e `localhost:8080` são origens diferentes (por isso o
CORS existe) mas o **mesmo site** — `SameSite=Lax` funciona de graça. Em produção,
dependeria de onde front e back forem hospedados, decisão que **ainda não foi tomada** e
que não deve ser forçada por essa tarefa.

**Escolha:** o front chama `/api/...` na própria origem e o servidor do Next repassa pro
Spring via `rewrites`.

Por quê:

- o cookie é sempre **first-party**, com `SameSite=Lax`, **independente da topologia de
  hospedagem** — a decisão de infra sai do caminho crítico;
- **CORS deixa de ser necessário** (tudo mesma origem);
- o axios faz double-submit de CSRF **automaticamente** em same-origin (ver abaixo);
- elimina a classe de bug "quebra no Safari por bloqueio de cookie de terceiro".

Custo: um salto de rede a mais (navegador → Next → Spring). Irrelevante no volume do piloto.
Reversível se um dia houver domínio único.

**Dev usa o mesmo caminho que prod.** `NEXT_PUBLIC_API_URL` passa a ser `/api` (relativo) nos
dois ambientes; o destino real do Spring vira uma env **server-side** do Next
(`API_INTERNAL_URL`, ex.: `http://localhost:8080` em dev), consumida só pelo `rewrites`.
Isso evita a classe de bug "passa em dev e quebra em prod" por caminhos divergentes.

**CORS é mantido, mas sai do caminho crítico.** A config atual (`app.cors.allowed-origins`,
`allowCredentials(true)`) fica como está: o tráfego do app passa a ser same-origin e não
depende dela, mas a API continua alcançável direto (curl, testes, debug). Removê-la seria
mexer em algo que não atrapalha — YAGNI.

### Alternativas descartadas

- **Cookie direto com `Domain=.seudominio` + `SameSite=Lax`.** Mais direto, sem salto
  extra, mas exige comprar domínio e hospedar os dois sob ele — exatamente a decisão que
  queremos adiar. Quebra se a API sair pra outro domínio.
- **`SameSite=None; Secure` cross-site.** Funciona em qualquer topologia sem proxy, mas
  cookie de terceiro é bloqueado por padrão em Safari e Brave, e o Chrome segue o mesmo
  caminho. Construir sobre algo que os navegadores estão matando.

## Arquitetura da sessão

### Os dois cookies

| Cookie | Conteúdo | Vida | Path |
|---|---|---|---|
| `domus_access` | JWT (o mesmo de hoje) | 10 min | `/` |
| `domus_refresh` | refresh opaco (Redis) | 7 dias | só rotas de `/auth` |

Ambos: `httpOnly` + `Secure` + `SameSite=Lax`.

**Path estreito no refresh:** o refresh token é a credencial mais valiosa que existe (com
ele se forja sessão nova por 7 dias). Não há motivo pra ele viajar em toda listagem de
membros. Analogia: o access token é o crachá que se mostra o dia todo; o refresh é a
certidão de nascimento — fica na gaveta.

**Consequência:** como o front chama `/api/auth/refresh` (o proxy só repassa), o `Path` do
cookie precisa ser escrito **na visão do navegador** — `/api/auth`. O Spring não adivinha
esse prefixo, então ele vira property de config (`app.cookie.path-prefix`).

**Rename:** `domus:token` → `domus_access`. O caractere `:` não é válido em nome de cookie
pela RFC 6265; os navegadores toleram hoje, mas não vale apostar a sessão na tolerância.

### Mudanças no backend

- `SecurityFilter.recoverToken` deixa de ler o header `Authorization` e passa a ler o
  cookie `domus_access`. **Sem fallback pro header** — mantê-lo deixaria o `localStorage`
  viável e tornaria a migração decorativa.
- `/auth/login`, `/auth/google/login`, `/auth/google/registrar` param de devolver tokens no
  corpo; emitem os dois cookies via `Set-Cookie` e devolvem só dados de exibição
  (`nome`, `role`, `igrejaId`, `igrejaNome`).
- `/auth/refresh` e `/auth/logout` param de ler o refresh do corpo e passam a lê-lo do
  cookie. `RefreshRequestDTO` é removido.
- Rotação, detecção de reuso, famílias de token e revogação: **inalterados**. O Redis não é
  tocado. Muda o *transporte* do token, não o modelo dele.
- `app.cookie.secure` (default `true`) como property. Navegadores tratam `localhost` como
  origem confiável, então `Secure` funciona em dev; a property é escape hatch.

### Novo endpoint: `GET /auth/me`

Hoje o front sabe que está logado porque o `localStorage` diz que está — o que funcionava
porque ele tinha o token na mão. Com `httpOnly`, **o front não consegue mais ler o cookie**
(esse é o ponto). Continuar confiando no `localStorage` seria o front **adivinhando**: o
cookie pode ter expirado e o `localStorage` seguir afirmando que há sessão. O resultado
seria o bug já caçado em 2026-07-16 — tela achando que está autenticada, disparando
request, tomando 401 e deslogando o usuário na cara.

Com `httpOnly`, **o servidor é o dono da verdade da sessão**. `GET /auth/me` lê o cookie e
devolve `{id, nome, role, igrejaId, igrejaNome}` ou 401.

Benefício adicional: acaba o problema de **role velha** no `localStorage` — hoje, se o
admin rebaixa alguém, o front segue achando que a pessoa é admin até relogar.

## CSRF

### Por que volta a ser obrigatório

Hoje o token vai no header `Authorization`, e **nenhum navegador manda header sozinho** —
só vai se o JS colocar. Por isso um site malicioso não forja requisição autenticada: ele
dispara a requisição, mas ela sai sem credencial. É isso, e só isso, que torna o
`.csrf(csrf -> csrf.disable())` atual aceitável.

Cookie inverte a propriedade: o navegador manda o cookie **sozinho, em toda requisição pro
domínio**, inclusive nas disparadas por outro site. A mesma conveniência que nos deixa
tirar o token do JS é a que abre o CSRF. Não dá pra ter uma sem a outra — por isso os dois
itens são **uma tarefa só**.

### Defesa em duas camadas

**1. `SameSite=Lax`** — o navegador não anexa o cookie em POST vindo de outro site. Sozinho
já mata a maioria esmagadora dos CSRF. `Lax` e não `Strict` porque `Strict` também segura o
cookie quando a pessoa chega por **link externo** — e existe exatamente esse fluxo (link do
e-mail de reset de senha). Com `Strict`, o usuário clicaria no link e cairia deslogado.

**2. Double-submit token** (`CookieCsrfTokenRepository` do Spring) — para não depender só do
navegador se comportar. O servidor manda o cookie `XSRF-TOKEN`; o front lê e devolve o
mesmo valor no header `X-XSRF-TOKEN`; o servidor compara.

Funciona porque o site atacante **consegue fazer o cookie ser enviado**, mas **não consegue
lê-lo** — a Same-Origin Policy proíbe ler cookie/resposta de outra origem. Sem ler, não
sabe qual valor pôr no header. O cookie é o cadeado; o header é a prova de que se enxerga a
mesma origem.

### Por que o `XSRF-TOKEN` NÃO é `httpOnly` (e isso não é contradição)

Ele é **deliberadamente legível por JS**. Parece contradizer tudo acima, mas **ele não é uma
credencial**: não prova quem você é, só prova que quem montou a requisição enxerga a mesma
origem. Um XSS lendo o `XSRF-TOKEN` não ganha nada, porque XSS **já roda dentro da origem** e
já podia fazer requisição autenticada de qualquer jeito.

São defesas contra atacantes diferentes: `httpOnly` defende do script injetado **dentro** da
página; CSRF defende do site **de fora**. Um não substitui o outro.

### Escopo e custo

- Só métodos que mudam estado (POST/PUT/PATCH/DELETE). GET fica de fora.
- **Custo no front ≈ zero:** os defaults do axios já são `xsrfCookieName: 'XSRF-TOKEN'` e
  `xsrfHeaderName: 'X-XSRF-TOKEN'`. Como o proxy nos deixa same-origin, ele faz sozinho.
  Ganho de brinde da decisão do proxy.

### Trade-off declarado: rotas públicas isentas

As rotas públicas de auth (`/auth/login`, `/auth/google/*`, `/igrejas/registrar`,
`/auth/forgot-password`, `/auth/reset-password`) ficam **isentas** do double-submit: elas
rodam quando **ainda não existe sessão**, então não há cookie de sessão pra um atacante
cavalgar — e protegê-las exigiria o front buscar um token CSRF antes de cada formulário
público, em 4 telas.

**Resíduo honesto:** fica possível o **login CSRF** (o atacante força a vítima a logar na
conta dele, e ela digita dados achando que é a própria). Ataque real, de impacto modesto
aqui, e o `SameSite=Lax` já o barra na prática (é POST cross-site). É escolha, não
ignorância. **Anotar no BACKLOG.**

## Front: estado e o destino do `proxy.ts`

### `authStore` encolhe

Saem `token`, `refreshToken` e `setTokens`. Sai o middleware **`persist` inteiro** — e com
ele o `localStorage` some da autenticação por completo, inclusive o `id` (pulga levantada em
2026-07-16). O store vira memória pura.

No load, `GET /api/auth/me` popula o store. Os campos usados pelas telas (`role`, `nome`,
`igrejaId`, `hidratado`, `isAuthenticated`) **mantêm nomes e seletores** — nenhuma página
precisa ser tocada. Muda só a *origem* do dado. `hidratado` muda de significado: de "o
localStorage foi lido" para "já perguntamos ao servidor quem somos". No login não há
round-trip extra — a resposta do `/auth/login` já traz os dados de exibição.

### `proxy.ts`: remover a lógica de auth

Quando o backend emitir o cookie de verdade, `domus_access` vai durar **10 minutos reais**.
O `proxy.ts` quebraria de forma cruel: 15 min idle → F5 → **chutado pro `/login` com a
sessão perfeitamente válida**, porque o refresh de 7 dias ainda estava lá mas o proxy não
olha pra ele. E ele **não pode** olhar: o refresh tem `Path=/api/auth`, então o navegador
nem manda esse cookie numa requisição de página.

**Decisão:** remover a lógica de auth do `proxy.ts`. Quem decide passa a ser `AuthGuard` +
`/auth/me`, que é a verdade real. Custo: instante de tela vazia ao entrar direto numa URL
privada — exatamente o que o `AuthGuard` já trata renderizando `null`.

**Alternativa descartada:** cookie de presença (`domus_sessao=1`, sem valor secreto,
não-`httpOnly`, `Path=/`, 7 dias) só pro proxy ler. Preservaria o redirect no servidor sem
custo de segurança, mas reintroduziria em menor escala a mesma mentira que estamos
removendo ("o cliente adivinha se tem sessão"), e é mais uma peça pra sincronizar com
login/logout/refresh.

## Tratamento de erro

- `renovarAccessToken()` deixa de ler o refresh do store e de chamar `setTokens()`; vira um
  `POST /api/auth/refresh` pelado. O cookie leva a credencial, o servidor reemite os
  cookies, o front não vê nem toca em token.
- O **single-flight** (um refresh por vez) **fica como está** — 401 simultâneos ainda
  dispararia refreshes paralelos que a rotação invalidaria entre si.
- `encerrarSessao()` passa a chamar `POST /api/auth/logout` (sem corpo) pro servidor
  **expirar os cookies**, já que o JS não consegue apagá-los.

Significado dos status:

| Status | Significado | Ação |
|---|---|---|
| `401` no `/auth/me` do load | não há sessão | `AuthGuard` → `/login`, sem barulho |
| `401` em rota qualquer | access expirado | fluxo de refresh de sempre |
| `403` de CSRF | header ausente/divergente | não deve ocorrer em uso normal; se ocorrer é bug nosso → deve chegar no Sentry, não ser engolido |

## Migração

No momento em que o `SecurityFilter` parar de ler o header `Authorization`, **toda sessão
existente morre**: quem estiver logado via `localStorage` toma 401 e cai no login.

Aceitável **porque ainda não há usuário real**. Depois da igreja entrar, exigiria janela de
convivência (ler cookie **e** header por um período). É o tipo de coisa barata agora e cara
depois.

Junto: limpeza única no load, apagando as chaves órfãs `domus:token` e `domus:auth` do
`localStorage`, pra não deixar token velho apodrecendo na máquina de ninguém.

## Testes

Convenção do projeto: Mockito puro, sem contexto Spring.

- Construção do cookie: nome, `httpOnly`, `Secure`, `SameSite`, `Path` e `Max-Age` corretos
  nos dois cookies.
- `SecurityFilter`: autentica lendo cookie; **ignora** o header `Authorization` — esse teste
  é o que garante que a migração não ficou decorativa.
- `/auth/refresh` e `/auth/logout` lendo do cookie; ausência de cookie → 401.
- `/auth/me`: com cookie válido devolve os dados; sem cookie → 401.

### Validação ao vivo (onde a verdade aparece)

- `curl -i` no login → conferir os atributos no `Set-Cookie`.
- No navegador, `document.cookie` **não pode** mostrar `domus_access`. Se mostrar, o
  `httpOnly` falhou e o trabalho todo foi em vão.
- `POST` sem o header `X-XSRF-TOKEN` → tem que dar **403**.
- 10+ min idle e navegar → refresh renova sozinho, sem deslogar.

## Critério de pronto

- Nenhum token em `localStorage`, em estado JS ou em cookie legível por JS.
- `document.cookie` não expõe `domus_access` nem `domus_refresh`.
- CSRF ativo e barrando POST sem header.
- Sessão sobrevive a 10+ min idle via refresh transparente.
- Testes unitários passando + validação ao vivo conferida.

## Fora de escopo (anotar no BACKLOG)

- **Login CSRF** nas rotas públicas isentas (ver trade-off acima).
- **Janela de convivência cookie+header** para migração sem deslogar todo mundo — só faria
  sentido com usuário real.
- **Decisão de hospedagem** — desacoplada de propósito por esta arquitetura.
