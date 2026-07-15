# Backlog — Dívida técnica & itens de próximo scope

> Lugar único para registrar **dívidas técnicas conscientes** e **features/itens deixados
> para depois** (fora do scope do piloto "minha igreja"). Sempre que uma decisão adiar algo,
> anotar aqui — para não se perder e resgatar quando abrir o próximo scope.
>
> Isto **não** substitui o roadmap (`CLAUDE.md`): o roadmap é o que vamos fazer agora, nas
> fases do piloto. Este arquivo é o que ficou **de fora** ou **para depois**.

---

## Dívida técnica (adiada de propósito, YAGNI/tempo)

- **Cache de `Usuario` no `SecurityFilter`.** Hoje o filtro de segurança bate no Postgres a
  cada requisição para carregar o usuário do JWT. Adiado por YAGNI (volume do piloto é baixo).
  Quando o tráfego crescer, cachear o usuário (ex.: Redis com TTL curto + invalidação em
  logout/alteração de role). Ver memória `cache-usuario-security-filter-adiado`.

- **Infra de teste de banco (Testcontainers).** O projeto não tem H2 nem Testcontainers; os
  testes de repositório rodam contra o Neon de testes (via `@AutoConfigureTestDatabase(replace=NONE)`)
  e exigem exportar as envs do `.env` no terminal. Ideal: subir um Postgres real em Docker por
  teste (fidelidade + isolamento), sem depender do Neon nem de env manual.

- **Maven wrapper quebrado.** Falta `.mvn/wrapper/maven-wrapper.properties`, então `./mvnw`
  não roda — usamos o `mvn` do sistema. Regerar o wrapper (`mvn wrapper:wrapper`).

- **CSP baseada em nonce no front (hardening).** A CSP atual do Next libera `'unsafe-inline'`
  e `'unsafe-eval'` (concessão ao Next.js sem nonce). Hardening real = CSP com nonce por
  requisição (via middleware), removendo os `unsafe-*`. Tarefa própria, considerável.

- **Rate limiting: migrar bloqueio de login para Redis.** Hoje o `LoginAttemptService` é em
  memória (some no restart, não escala entre instâncias). Migrar para Redis. (Parte do item de
  rate limiting da Fase 1 — mas o *storage* em Redis é a dívida específica.)

- **Aviso do Mockito (self-attaching agent).** Testes logam warning de que o Mockito se
  auto-anexa como agente; em JDKs futuros deixará de funcionar. Configurar o byte-buddy/mockito
  como Java agent no surefire.

---

## Fora do scope do piloto (próximo scope / camada comercial)

- **Integração com Google Calendar (agendar eventos).** Módulo **separado** de autorização de
  API — Authorization Code flow com `access_type=offline` + escopo `calendar`, guardando o
  refresh token do Google **por usuário**, com botão explícito "Conectar agenda". NÃO se mistura
  com o login (que é só identidade). Ver memória `google-oauth-auth`.

- **Fluxo de convite por e-mail (novo provisionamento).** Hoje o admin, ao "conceder acesso",
  define a senha do membro. Desejado: admin só escolhe a role e **convida por e-mail**; o próprio
  usuário define a senha (reusa o reset). Já movido para a **Fase 2** do roadmap (o Google OAuth
  já deixou `senha_hash` nullable, então o back está pronto para usuários sem senha).

- Itens já listados em "Fora do escopo desta versão" no `CLAUDE.md` (filtros extras em
  financeiro, múltiplos atribuintes, verificação de posse de telefone via SMS, expansão de
  campos de membro por uso real) — mantidos lá; referência cruzada aqui.

---

## Warnings conhecidas e benignas (não são bug)

- **Botão do Google Sign-In em dev:** o console loga `[GSI_LOGGER]: The given origin is not
  allowed for the given client ID` (403 no render do botão) e `Cross-Origin-Opener-Policy would
  block the window.postMessage call`. Ambas aparecem em Chrome e Firefox, mas **login e cadastro
  funcionam** (o sign-in real usa popup). São do lado do Google (script `client:380`), não da
  nossa CSP/COOP (não setamos COOP). Verificado em 2026-07-15. Em prod (HTTPS + domínio real)
  tende a sumir. Se incomodar em dev: conferir a origem em "Authorized JavaScript origins" e
  aguardar propagação.

## Bugs conhecidos (corrigir na fase apropriada)

- **`Sidebar.tsx` referencia `state.foto`** que não existe em `AuthState` → erro de tipo
  pré-existente. Corrigir junto da feature de **upload de foto** (Fase 2).
