# Spec de Design: Travas de Limite SaaS, Convite de Congregação, Modal de Upgrade & Testes E2E

> **Data:** 2026-09-25  
> **Status:** Aprovado  
> **Escopo:** Trava backend de pessoas ativas (`limitePessoas`), cadastro público de congregações vinculadas via código de convite (pulando checkout), modal de upgrade animado e reutilizável (`ModalUpgradePlano`), interceptor e componentes de Feature Flags (`FeatureGuard`), esteira de auditoria de segurança (Workflow Ultracode) e testes E2E Playwright.

---

## 1. Objetivos e Regras de Negócio

1. **Trava de Pessoas Ativas no Backend (`limitePessoas`)**:
   - `PlanoAssinatura`: `BASICO` (60 pessoas), `PRO` (300), `PRO_PLUS` (800), `ENTERPRISE` (99999).
   - No `PessoaService.criar(...)`, conta pessoas ativas (`countByIgrejaIdAndDeletedAtIsNull(igrejaId)`).
   - Se `totalPessoas >= plano.limitePessoas`, dispara `PlanoLimiteExcedidoException` (HTTP 402 / 400) com código `LIMITE_PESSOAS_EXCEDIDO`.

2. **Fluxo de Cadastro de Congregação Filha via Código de Convite**:
   - Matriz acessa `/configuracoes/igrejas-vinculadas` ou `/configuracoes/assinatura` e clica em **"Gerar Código de Convite"**.
   - O backend valida se `countByIgrejaMaeId(matriz.getId()) < matriz.getPlano().getLimiteCongregacoes()`.
   - Gera um código alfanumérico único e unambríguo `DOMUS-XXXXXX` (ex: `DOMUS-K7M9P2`) e salva em `codigo_convite_congregacao`.
   - A igreja filha abre `/cadastro/congregacao?codigo=DOMUS-K7M9P2`, preenche os dados da congregação e do líder admin, e conclui o cadastro **sem passar pelo checkout de pagamento**.
   - A nova congregação nasce com `igreja_mae_id = matriz.getId()` e `status_assinatura = ATIVA`.
   - O código é marcado como utilizado (`usado_em = NOW()`).

3. **Modal de Upgrade Reutilizável (`ModalUpgradePlano`)**:
   - Componente UI padronizado do Domus com suporte a responsive design (bottom-sheet no mobile com `.grabber`, curva easeOut, animações CSS).
   - Usado em:
     a) Tentativa de cadastrar pessoa além do limite do plano.
     b) Tentativa de gerar código de congregação além do limite.
     c) Tentativa de acessar funcionalidade restrita por plano (`<FeatureGuard />`).
   - Apresenta:
     - Ícone e título ilustrativo com cor acentuada.
     - Barra de progresso / indicador do limite atual (ex: 60/60 pessoas).
     - Explicação clara sobre o motivo do bloqueio.
     - Botão CTA animado **"Fazer Upgrade de Plano"** que redireciona para `/configuracoes/assinatura` ou `/planos`.

4. **Testes E2E (Playwright)**:
   - Suíte `frontend/e2e/saas-assinatura-limites.spec.ts` validando:
     - Exibição de planos em `/planos`.
     - Bloqueio de funcionalidade e exibição do modal de upgrade.
     - Fluxo de cadastro de congregação via código de convite.

---

## 2. Componentes e Alterações Técnicas

### Backend (Java / Spring Boot)
1. **`PessoaService.java`**:
   - Adiciona validação do limite de pessoas antes de criar nova pessoa.
2. **`GlobalExceptionHandler.java`**:
   - Trata `PlanoLimiteExcedidoException` devolvendo HTTP 402 com payload estruturado (`error`, `message`, `plano`, `limite`, `atual`).
3. **`CadastroCongregacaoController.java` & `CadastroCongregacaoService.java`**:
   - Endpoint `POST /api/igrejas/registrar-congregacao` aceitando `CadastroCongregacaoRequest` (`codigoConvite`, `nomeCongregacao`, `nomeAdmin`, `emailAdmin`, `senha`).
   - Valida e consome o código via `CodigoConviteService`.
4. **`CodigoConviteController.java`**:
   - Endpoint `POST /api/igrejas-vinculadas/codigo-convite` (protegido por role `ADMIN_IGREJA`) para a matriz gerar novos códigos.

### Frontend (Next.js / TypeScript)
1. **`<ModalUpgradePlano />`**:
   - Localizado em `src/components/common/ModalUpgradePlano/ModalUpgradePlano.tsx`.
   - Props: `aberto`, `aoFechar`, `titulo`, `descricao`, `planoAtual`, `planoSugerido`, `progressoCurrent`, `progressoMax`.
2. **`CadastroCongregacaoPage` (`src/app/(public)/cadastro/congregacao/page.tsx`)**:
   - Conectado ao endpoint `POST /api/igrejas/registrar-congregacao`.
   - Lê `?codigo=` da URL e auto-preenche o campo de código de convite.
3. **`SecaoIgrejasVinculadas` / `/configuracoes/igrejas-vinculadas/page.tsx`**:
   - Botão **"Gerar Convite para Congregação"** chamando a API e abrindo modal com o código e link para cópia de 1 clique (`https://domusigreja.com.br/cadastro/congregacao?codigo=DOMUS-XXXXXX`).
4. **`PessoaForm` / `useCadastrarPessoa`**:
   - Intercepta erro `LIMITE_PESSOAS_EXCEDIDO` e abre `<ModalUpgradePlano />`.

---

## 3. Matriz de Cobertura de Testes

| Camada | Teste | Cenário |
|---|---|---|
| **Backend Unit/Integration** | `PessoaServiceTest` | Valida recusa de cadastro quando `countByIgrejaId >= limitePessoas` |
| **Backend Integration** | `CodigoConviteServiceTest` | Valida geração de código, consumo por congregação e bloqueio ao estourar `limiteCongregacoes` |
| **Backend Controller** | `CadastroCongregacaoControllerTest` | Valida endpoint `POST /api/igrejas/registrar-congregacao` pulando checkout |
| **Frontend E2E** | `saas-assinatura-limites.spec.ts` | Teste de ponta a ponta no Chromium + WebKit cobrindo `/planos`, modal de upgrade e convite de congregação |
