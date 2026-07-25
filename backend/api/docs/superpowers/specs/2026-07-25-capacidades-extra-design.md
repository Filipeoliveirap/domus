# Capacidades extra (SECRETARIO / TESOUREIRO) — design

> Terceiro de três specs derivados do brainstorm de 2026-07-25 (célula, visitantes,
> capacidades extra). **Independente** dos outros dois — não depende de Visitantes nem
> de Células existirem, mas o item "`podeGerenciarVisitantes` estende com
> `SECRETARIO`" só faz sentido depois que aquela função existir (spec de Visitantes).
> Se este spec for implementado antes, essa linha específica fica pendente até lá.

## Motivação

Hoje cada usuário tem exatamente uma role (`ADMIN_IGREJA`/`LIDER`/`ACESSO_COMUM`), e
isso não cobre o caso real: uma pessoa pode ser líder de um ministério/célula **e**
também cuidar da secretaria (gerência de pessoas e visitantes), ou cuidar do
financeiro **e** também ser líder de algo. Hoje isso é impossível — a pessoa só pode
ter uma das duas.

## Decisão de arquitetura: aditivo, não substitutivo

Cogitamos dois caminhos: reescrever pra um modelo de múltiplas roles de verdade (N pra
N, qualquer combinação) ou manter a role base como está e adicionar "capacidades
extra" que qualquer usuário acumula por cima. **Escolhido: capacidades extra.**

**Por quê:** o modelo de múltiplas roles de verdade exigiria mudar toda checagem de
permissão existente no front e no back (dezenas de lugares) — uma reescrita grande e
arriscada numa área que já está em produção, pra resolver um problema que na prática é
bem mais estreito: só duas capacidades novas (`SECRETARIO`, `TESOUREIRO`), que se
somam à role base sem entrar em conflito com ela. O modelo aditivo resolve exatamente
isso com risco muito menor: a role base continua exatamente como é hoje, e só as
funções de `Permissoes` que `SECRETARIO`/`TESOUREIRO` devem estender precisam mudar de
assinatura — o resto do arquivo (e todo o resto do sistema de autorização) fica
intocado.

## Modelo de dados

```sql
CREATE TABLE usuario_capacidade (
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    capacidade  VARCHAR(20) NOT NULL,  -- SECRETARIO | TESOUREIRO
    concedido_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (usuario_id, capacidade),
    CONSTRAINT chk_usuario_capacidade_valor CHECK (capacidade IN ('SECRETARIO', 'TESOUREIRO'))
);
```

Sem soft delete: revogar uma capacidade é `DELETE` da linha — reversível (admin
readiciona se errar), sem necessidade de histórico. A role base
(`usuario.role_id` → `ADMIN_IGREJA`/`LIDER`/`ACESSO_COMUM`) não muda em nada — continua
sendo exatamente uma por usuário, como hoje.

## O que cada capacidade estende

- **`SECRETARIO`** estende: `podeGerenciarPessoas`, `podeVerDadosSensiveisDePessoa`,
  `podeGerenciarVisitantes` (esta última definida no spec de Visitantes,
  `2026-07-25-visitantes-design.md`).
- **`TESOUREIRO`** estende: `podeVerFinanceiro`, `podeVerUsuariosEFinanceiroNaBuscaGlobal`.
- Nenhuma outra função de `Permissoes`/`permissoes.ts` muda. Essas 5 funções (contando
  a de Visitantes) passam a receber um segundo parâmetro — o conjunto de capacidades
  extra do usuário — e resolvem `true` se a role base já permitir OU se o conjunto
  contém a capacidade correspondente.

```java
// Antes:
public static boolean podeGerenciarPessoas(String role) { return tem(role, SO_ADMIN); }

// Depois:
public static boolean podeGerenciarPessoas(String role, Set<String> capacidadesExtras) {
    return tem(role, SO_ADMIN) || capacidadesExtras.contains("SECRETARIO");
}
```

## Backend

- As 5 funções de `Permissoes.java` (`podeGerenciarPessoas`, `podeVerDadosSensiveisDePessoa`,
  `podeGerenciarVisitantes`, `podeVerFinanceiro`, `podeVerUsuariosEFinanceiroNaBuscaGlobal`)
  ganham o segundo parâmetro `Set<String> capacidadesExtras`. Todos os call sites (nos
  controllers de `pessoa`, `visitante`, `financeiro`, busca global) passam a chamar com
  o segundo argumento.
- `UsuarioAutenticado` ganha `getCapacidadesExtras(): Set<String>`, carregado junto com
  o resto da sessão (mesmo mecanismo que a role já usa hoje — checar a implementação
  real de como a role chega até `UsuarioAutenticado`/JWT antes de decidir se
  capacidades extra entram no token ou são consultadas à parte; qualquer que seja o
  mecanismo, precisa refletir mudança de capacidade sem exigir logout/login completo
  sempre que possível, dentro do que a arquitetura de sessão atual permitir).
- Endpoints novos (`ADMIN_IGREJA`-only, mesma gestão de `Usuários` de hoje):
  - `POST /usuarios/{id}/capacidades` `{capacidade: SECRETARIO|TESOUREIRO}` — concede
    (idempotente: conceder de novo uma capacidade já concedida não é erro)
  - `DELETE /usuarios/{id}/capacidades/{capacidade}` — revoga (idempotente: revogar
    uma capacidade que a pessoa não tem não é erro, só não faz nada)
- `GET /usuarios`, `GET /usuarios/{id}` e `GET /auth/me` passam a incluir
  `capacidadesExtras: string[]` na resposta.

## Frontend

- `useAuthStore` ganha `capacidadesExtras: string[]`, populado no login/`GET /auth/me`
  (mesmo lugar que popula `role` hoje).
- As 5 funções equivalentes em `permissoes.ts` ganham o segundo parâmetro, espelhando
  o back — todo call site precisa passar `capacidadesExtras` do store além da `role`.
- Tela `/usuarios`: ao editar o acesso de alguém, além do seletor de role já existente,
  dois checkboxes — "Secretário" e "Tesoureiro" — que concedem/revogam na hora, sem
  confirmação reforçada (reversível, baixo risco). Badge extra ao lado da role na
  tabela quando a pessoa tem alguma capacidade (ex.: `LIDER` + selo "Secretário").

### Erros

- Conceder/revogar capacidade de usuário de outra igreja → 404 (isolamento multi-tenant,
  igual todo o resto do sistema).
- Valor de `capacidade` fora de `SECRETARIO`/`TESOUREIRO` → 400.

## Fora de escopo

- Modelo de múltiplas roles de verdade (N pra N) — decisão já tomada acima; revisitar
  só se o uso real da Fase 5 comercial realmente exigir combinações mais livres.
- Qualquer capacidade extra além de `SECRETARIO`/`TESOUREIRO` — adicionar uma nova
  ainda exige editar as funções de `Permissoes` que ela deve estender (o enum/tabela já
  está pronto pra crescer, mas "quais funções ela estende" continua sendo uma decisão
  de código, não de dado).
- Histórico de concessão/revogação de capacidade (sem soft delete) — extensão futura
  se precisar de auditoria completa.
- Interface para o próprio usuário ver quais capacidades tem (fica implícito pelo que
  ele consegue acessar no menu) — sem tela dedicada "minhas permissões".
