# Spec de Design: Travas de Limite SaaS, Convite de Congregação, Resgate com Feedback Visual & E2E

> **Data:** 2026-09-25  
> **Status:** Aprovado  
> **Escopo:** Trava backend de pessoas ativas (`limitePessoas`), cadastro público de congregações vinculadas via código de convite (pulando checkout), modal de upgrade animado e reutilizável (`ModalUpgradePlano`), tela e modal de convite com layout SaaS moderno (baseado em protótipo Stitch/Tailwind), tratamentos diferenciados para erros de convite (link expirado, código usado, limite do plano atingido) e testes E2E Playwright.

---

## 1. Visão Geral e Requisitos de Negócio

1. **Trava de Pessoas Ativas no Backend (`limitePessoas`)**:
   - `PlanoAssinatura`: `BASICO` (60 pessoas), `PRO` (300), `PRO_PLUS` (800), `ENTERPRISE` (99999).
   - No `PessoaService.criar(...)`, conta pessoas ativas (`countByIgrejaIdAndDeletedAtIsNull(igrejaId)`).
   - Se `totalPessoas >= plano.limitePessoas`, dispara `PlanoLimiteExcedidoException` (HTTP 402 / 400) com código `LIMITE_PESSOAS_EXCEDIDO`.

2. **Fluxo de Cadastro de Congregação Filha via Código de Convite**:
   - Matriz acessa `/configuracoes/igrejas-vinculadas` ou `/configuracoes/assinatura` e clica em **"Gerar Código de Convite"**.
   - O backend valida se `countByIgrejaMaeId(matriz.getId()) < matriz.getPlano().getLimiteCongregacoes()`.
   - Gera um código alfanumérico único e unambríguo `DOMUS-XXXXXX` (ex: `DOMUS-K7M9P2`) e salva em `codigo_convite_congregacao`.
   - A igreja filha abre `/cadastro/congregacao?codigo=DOMUS-K7M9P2` ou clica na Landing Page em **"Resgatar Convite de Filial / Já tem convite?"**, preenche os dados da congregação e do líder admin, e conclui o cadastro **sem passar pelo checkout de pagamento**.
   - A nova congregação nasce com `igreja_mae_id = matriz.getId()` e `status_assinatura = ATIVA`.
   - O código é marcado como utilizado (`usado_em = NOW()`).

3. **Experiência Visual e Tratamento Diferenciado de Erros de Convite**:
   - **Visual SaaS Moderno**: Layout inspirado no protótipo Stitch/Tailwind (header com logo oficial do Domus, cards com sombra suave, badges acentuados, informações da Igreja Sede como pastor, plano e endereço).
   - **Logos Oficiais**: Troca de qualquer logo genérica para a logo oficial do Domus (`/images/logo.png`).
   - **Tratamento por Tipo de Erro**:
     - **Link/Código Expirado**: Usa o componente padrão de Link Expirado (`TimerOff`/`iconBadgeError`), instruindo a solicitar novo código.
     - **Código Já Utilizado**: Usa o componente padrão com mensagem customizada de feedback ("Este código de convite já foi utilizado por outra congregação").
     - **Limite de Congregações do Plano Atingido**: Exibe tela/card específico orientando entrar em contato com a igreja matriz `{nomeDaMatriz}` e convidando a conhecer os planos do Domus (`/planos`).

4. **Modal de Upgrade Reutilizável (`ModalUpgradePlano`)**:
   - Componente UI padronizado do Domus com suporte a responsive design (bottom-sheet no mobile com `.grabber`, curva easeOut, animações CSS).
   - Usado em:
     a) Tentativa de cadastrar pessoa além do limite do plano.
     b) Tentativa de gerar código de congregação além do limite.
     c) Tentativa de acessar funcionalidade restrita por plano (`<FeatureGuard />`).

5. **Testes E2E (Playwright)**:
   - Suíte `frontend/e2e/saas-assinatura-limites.spec.ts` validando:
     - Exibição de planos em `/planos`.
     - Bloqueio de funcionalidade e exibição do modal de upgrade.
     - Fluxo de cadastro de congregação via código de convite e resgate pela Landing Page.

---

## 2. Componentes Técnicos

### Backend (Java / Spring Boot)
1. **`PessoaService.java`**:
   - Adiciona validação de `limitePessoas` ativas.
2. **`GlobalExceptionHandler.java`**:
   - Trata `PlanoLimiteExcedidoException` devolvendo HTTP 402 com payload estruturado.
3. **`CadastroCongregacaoController.java` & `CadastroCongregacaoService.java`**:
   - Endpoint `POST /igrejas/registrar-congregacao` (público) e `POST /igrejas-vinculadas/codigo-convite` (protegido por `ADMIN_IGREJA`).
   - Endpoint `GET /convites/congregacao/{codigo}` (público) para consultar a matriz e estado do convite antes de cadastrar.

### Frontend (Next.js / TypeScript)
1. **Landing Page (`src/app/(public)/page.tsx`)**:
   - Botão no Hero/Banner: **"Resgatar Convite de Filial"** que abre modal ou redireciona para `/cadastro/congregacao`.
2. **`CadastroCongregacaoPage` (`src/app/(public)/cadastro/congregacao/page.tsx`)**:
   - Layout visual moderno baseado no protótipo SaaS.
   - Consulta estado do convite via API e alterna entre os estados:
     - Validação OK: Card verde com resumo da Matriz e botão "Concluir Cadastro Gratuitamente".
     - Erro Expirado/Inexistente: Componente de Link Expirado.
     - Erro Já Usado: Componente com mensagem de código já utilizado.
     - Erro Limite Excedido da Matriz: Card de orientação para falar com a matriz `{nomeMatriz}` + Link para `/planos`.
3. **`<ModalUpgradePlano />`**:
   - Modal responsivo animado para avisos de upgrade de plano.

---

## 3. Matriz de Cobertura de Testes

| Camada | Teste | Cenário |
|---|---|---|
| **Backend Unit/Integration** | `PessoaServiceTest` | Valida recusa de cadastro quando `countByIgrejaId >= limitePessoas` |
| **Backend Integration** | `CodigoConviteServiceTest` | Valida geração de código, consumo por congregação e bloqueio ao estourar `limiteCongregacoes` |
| **Backend Controller** | `CadastroCongregacaoControllerTest` | Valida endpoint `POST /igrejas/registrar-congregacao` |
| **Frontend E2E** | `saas-assinatura-limites.spec.ts` | Teste Playwright cobrindo `/planos`, resgate na Landing Page e convite de congregação |
