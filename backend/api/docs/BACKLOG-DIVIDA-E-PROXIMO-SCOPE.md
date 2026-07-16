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

- ~~**Rate limiting: migrar bloqueio de login para Redis.**~~ **FEITO** (2026-07-16): o
  `LoginAttemptService` agora usa Redis (`login:attempt:*`/`login:block:*`). Junto entrou o
  rate limiting geral por IP (`RateLimitFilter`, janela fixa, global 100/min + auth 10/min).
  O que ficou **de fora** e pode virar dívida no futuro:
    - **Algoritmo sliding window / token bucket.** A janela fixa tem efeito de borda (pode-se
      mandar ~2x o limite na virada do minuto). Irrelevante pro piloto; trocar por Bucket4j se
      o volume exigir (o filtro é o único ponto de troca).
    - **Limite por usuário autenticado.** Hoje é só por IP. Um limite por `usuario_id` daria
      granularidade extra (ex.: um usuário abusando de dentro de uma rede compartilhada/NAT).
    - **Limites por rota individual.** Hoje há só dois tiers (global e auth). Se algum endpoint
      específico precisar de teto próprio, generalizar a configuração.

- **Aviso do Mockito (self-attaching agent).** Testes logam warning de que o Mockito se
  auto-anexa como agente; em JDKs futuros deixará de funcionar. Configurar o byte-buddy/mockito
  como Java agent no surefire.

---

## Segurança / autorização — a discutir (decisão de produto)

- **Acesso horizontal a dados de membro dentro da mesma igreja (a decidir a intenção).**
  Hoje `GET /membros/**` permite o perfil `MEMBRO`, e `buscarPorId` escopa **só por igreja**
  (`findByIdAndIgrejaId`), **sem checagem de dono**. Consequência: qualquer `MEMBRO` da igreja
  vê os dados completos de qualquer outro membro (`email`, `telefone`, `endereco`,
  `dataNascimento`, `observacoes`, etc.) — seja por `GET /membros/{id}` ou pela listagem
  `GET /membros`. **Não é falha de sigilo de id** (o id não é segredo; a autorização é que
  decide) — é uma decisão de controle de acesso.
  - *Perguntar:* isso é intencional (lista de contatos aberta a toda a igreja) ou os campos
    sensíveis (endereço, telefone, `observacoes` — possíveis notas pastorais privadas) deveriam
    ser restritos a ADMIN/LÍDER?
  - *Opções se for restringir:* (a) tirar `MEMBRO` do `GET /membros`; (b) filtrar campos por
    perfil (membro vê perfil reduzido); (c) membro só vê o próprio registro.
  - *Escopo maior:* fazer uma **revisão de autorização por perfil em todos os módulos** (quem vê
    o quê) — não só membros. Descoberto em 2026-07-16 discutindo o risco de id em localStorage.

---

## Observabilidade — fora do escopo da entrega de Sentry (2026-07-16)

O Sentry (back + front) e os logs estruturados foram feitos. Ficou para depois:
- **Upload de source maps + release tracking** (front): precisa de `SENTRY_AUTH_TOKEN` no
  build de CI/prod pra o stack trace do Sentry apontar pro código original (não o minificado).
  `withSentryConfig` já está pronto pra isso; falta o token + pipeline.
- **APM / performance tracing** (`tracesSampleRate` está 0 nos dois lados) — só se houver
  necessidade real de medir latência; consome cota do tier grátis.
- **Envio de logs pra um agregador central** (Loki/ELK/CloudWatch). Hoje os logs JSON saem no
  stdout; em prod alguém precisa coletá-los. Depende de onde a app for hospedada.
- **Afinação de regras de alerta** no painel do Sentry (quais erros notificam, para quem).

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
  pré-existente. **⚠️ Quebra o `next build` inteiro** (typecheck do build de produção falha),
  então hoje o front não fecha build de prod. Corrigir junto da feature de **upload de foto**
  (Fase 2) — ou antes, se for necessário fechar um build de produção.
