# Redesign do wizard de cadastro de igreja (`/cadastro`)

**Data:** 2026-09-08
**Tipo:** frontend, só apresentação (sem backend, sem mudança de validação ou fluxo de dados)

## Objetivo

Transformar os 3 passos do cadastro — hoje três layouts de página distintos que
trocam com corte seco — num wizard coeso: um shell único, painel esquerdo
persistente, e o formulário da direita trocando com transição direcional. O passo
3 (boas-vindas) ganha logo do Domus e um acabamento mais profissional. Tudo
funciona bem no mobile, onde não há painel/foto.

## Não-objetivos

- Nenhuma mudança em `authService.registrarIgreja` / `googleRegistrar`, nos
  schemas Zod (`registrarIgrejaSchema1/2`), ou na sequência de passos e regras.
- Não mexer no fluxo Google além de adaptá-lo ao shell (continua pulando o passo 2).
- Não adicionar ilustração/arte nova além do que já existe (`sanctuary.jpg`, logo).
- Sem confete/emoji de festa — o tom é sóbrio.

## Estado atual (o que existe)

- `src/app/(auth)/cadastro/page.tsx` (165 linhas): renderiza um de três blocos
  de página conforme `passo` (1|2|3) vindo de `useRegistrarIgreja`. Cada bloco tem
  seu próprio `<div className={styles.pageX}>` com layout independente
  (`.card` grid 5/7 / `.page2` grid 1/1 / `.pageSucesso`).
- `Passo1.tsx`, `Passo2.tsx`: conteúdo dos formulários, mas cada um traz seu
  próprio `<header>` com título/subtítulo e sua própria action bar.
- `SanctuaryPanel.tsx`: foto + frase + "DOMUS" — só no passo 1.
- `ProgressIndicator.tsx`: barra de passos — instanciada dentro de cada bloco.
- `SecurityFooter.tsx`: rodapé "conexão segura" — passos 1 e 2.
- `Page.module.css` (362 linhas): os três layouts + estilos do passo 3 (atalhos).
- `useRegistrarIgreja.ts`: `passo`, `irParaPasso2`, `voltarParaPasso1`, os dois
  formulários (RHF), `onSubmit`/`onSubmitGoogle`, `dadosSucesso`, `googleData`,
  os quatro `irPara*` do passo 3.

## Arquitetura nova

`page.tsx` fica fino: instancia `useRegistrarIgreja` e passa tudo pro
`<CadastroShell>`. O shell é o card unificado.

```
page.tsx
└─ CadastroShell            card unificado (grid painel | form no desktop; só form no mobile)
   ├─ PainelWizard          coluna esquerda persistente (desktop) / barra de topo (mobile)
   └─ TrocaPasso            palco que anima a troca de cena do formulário
      ├─ Passo1  (cena)     conteúdo puro do form da igreja
      ├─ Passo2  (cena)     conteúdo puro do form do admin
      └─ Passo3  (cena)     tela de boas-vindas redesenhada
```

### `CadastroShell.tsx` (novo)

- **O que faz:** monta o card (`.shell`), decide o layout (desktop = grid
  `painel | form`; `@media (max-width: 900px)` = só o form, com `PainelWizard`
  no modo barra). Passa `passoAtual` e `totalPassos` pro `PainelWizard`, e a
  cena + direção pro `TrocaPasso`.
- **Props:** todo o retorno de `useRegistrarIgreja` (repassado). `totalPassos`
  derivado: `googleData ? 2 : 3`.
- **Depende de:** `PainelWizard`, `TrocaPasso`, `Passo1/2/3`, `SecurityFooter`.
- O `SecurityFooter` fica no rodapé da área de form (passos 1 e 2; some no 3).

### `PainelWizard.tsx` (novo — absorve `SanctuaryPanel` + `ProgressIndicator`)

- **O que faz:** a identidade visual persistente.
  - **Desktop:** coluna com `sanctuary.jpg` de fundo (véu escuro em degradê),
    por cima: marca (logo + "DOMUS") no topo; no meio a **copy que evolui**
    (rótulo + headline + texto, ver tabela abaixo); no rodapé o progresso em
    **círculos numerados** (`1 ✓` / `2` atual com halo / `3`), com linhas que
    preenchem entre eles.
  - **Mobile (`max-width: 900px`):** vira uma **barra no topo do form** —
    logo + "DOMUS" pequeno, 3 **segmentos** que preenchem, e o rótulo
    `Passo N de T · <título curto>`. Sem foto.
  - **Passo 3:** o fundo ganha um brilho radial suave (sem partículas).
- **Props:** `passoAtual: 1|2|3`, `totalPassos: 2|3`, `nomeAdmin?: string`
  (pra headline do passo 3).
- **Depende de:** só CSS + `next/image`.
- **Reduced-motion:** brilho e transições de largura das barras viram estáticos.

### `TrocaPasso.tsx` (novo)

- **O que faz:** anima a troca entre cenas do formulário. Direcional:
  - avançar (`direcao > 0`): cena atual desliza pra esquerda + fade-out; a nova
    entra da direita + fade-in.
  - voltar (`direcao < 0`): o inverso.
  - a altura do palco acompanha a cena nova (evita salto quando o passo 2 é
    mais alto que o 1).
  - `~340ms`, curva `cubic-bezier(0.22, 1, 0.36, 1)`.
- **Por que não reusar `<TrocaCena>`:** o `TrocaCena` faz slide **vertical**
  (sobe) com `cenaKey` arbitrário e não conhece direção. O wizard tem passos
  finitos e ordenados e precisa de **horizontal direcional**. Mantê-los
  separados evita inchar o `TrocaCena` com um modo que só o wizard usa.
- **Props:** `passo: 1|2|3`, `direcao: 1 | -1`, `children` (a cena já resolvida
  pelo shell — render-prop não é necessário aqui: a cena que sai continua
  montada até o fim da animação via cópia do nó, como no `TrocaCena`, pra não
  piscar).
- **Padrão de implementação:** ajuste de estado em render ao mudar `passo`
  (mesma técnica do `TrocaCena`/`useReordenacaoAnimada`), `useLayoutEffect` pra
  medir/animar altura, `setTimeout` pra remover a cena antiga. Nada de `setState`
  síncrono em `useEffect`.
- **Reduced-motion:** troca instantânea (sem slide, sem animação de altura).

### `Passo1.tsx` / `Passo2.tsx` (modificados)

- Perdem o `<header>` próprio (título/subtítulo passam a ser responsabilidade
  do shell/`PainelWizard` no mobile, e um `<h3>` no topo da área de form no
  desktop) e a action bar de página. Viram o **miolo** do formulário:
  campos + botões de ação. As props RHF continuam iguais.
- A action bar (Voltar / Próximo|Criar conta) fica no rodapé da cena, renderizada
  **por cada passo** (o texto e a ação do botão mudam por passo), usando uma
  classe `.acoes` compartilhada definida no CSS do shell/painel pra o visual ser
  idêntico entre passos.
- Passo 1 mantém: botão Google, divisor "OU PREENCHA MANUALMENTE", banner
  "Cadastrando como X" + checkbox de termos no modo Google.

### `Passo3.tsx` (novo — extraído de `page.tsx`, redesenhado)

- **Conteúdo:** logo Domus (bloco arredondado com sombra) que **escala e
  aparece** na entrada; um check verde discreto; `<h2>Igreja criada</h2>`;
  linha "A **{nomeIgreja}** já está no ar. Bem-vindo, **{primeiro nome}**.";
  os **3 atalhos** (Cadastrar pessoas — destaque / Completar perfil da igreja /
  Completar meu perfil) que **sobem em cascata** (~100ms entre eles);
  "Pular e ir pro painel inicial".
- **Props:** `nomeIgreja`, `nome`, `irParaPessoas`, `irParaPerfilIgreja`,
  `irParaMeuPerfil`, `irParaPainelInicial` (já vêm do hook).
- **Depende de:** `next/link` ou os callbacks do hook, CSS, ícones lucide
  (`Users`, `Building2`, `UserCog`, `ArrowRight`, `Check`).
- **Reduced-motion:** logo/check/atalhos aparecem sem animação.

### `useRegistrarIgreja.ts` (mudança mínima)

1. Expor a **direção** da última troca de passo pro `TrocaPasso`:
   `direcaoPasso: 1 | -1` — setado em `irParaPasso2`/`onSubmit`/`onSubmitGoogle`
   (→ `1`) e em `voltarParaPasso1` (→ `-1`). Também `1` ao entrar no passo 3.
2. No sucesso (antes de `setPasso(3)`, nos dois caminhos): chamar
   `marcarBoasVindasVista()` (de `@/lib/boasVindas`). Sem isso, a animação
   "curta" de boas-vindas do `<PortalBoasVindas>` toca de novo quando a pessoa
   abrir o app pela primeira vez — o passo 3 já é a boas-vindas.

## Copy que evolui (painel)

| Passo | Rótulo | Headline | Texto | Título curto (mobile) |
|---|---|---|---|---|
| 1 | Vamos começar | Leva menos de 2 minutos | Cadastre sua igreja e crie sua conta de administrador. | Dados da igreja |
| 2 | Falta pouco | Sua conta de administrador | Só mais alguns dados e o Domus é seu. | Sua conta de admin |
| 3 | Pronto | Tudo certo, {primeiro nome} | Sua igreja foi criada. Escolha por onde começar. | Tudo pronto |

No fluxo Google (`totalPassos = 2`), o passo exibido é "1 de 2" e depois "2 de 2"
(o passo 3 é o segundo). Os rótulos/headlines do passo 1 e 3 continuam valendo; o
passo 2 nunca aparece.

## CSS

`Page.module.css` (362 linhas com 3 layouts) consolida. Uma proposta de divisão:

- `CadastroShell.module.css` — `.page` (fundo gradiente + decorações blur, hoje
  duplicado em `.page` e `.page2`), `.shell` (o card grid), área de form,
  action bar, responsivo.
- `PainelWizard.module.css` — painel desktop (foto, véu, copy, progresso em
  círculos) + barra mobile (segmentos).
- `Passo3.module.css` — logo, check, atalhos, animações de entrada.
- `Passo1.module.css` / `Passo2.module.css` — enxugam (perdem header/action bar
  próprios).

`SanctuaryPanel.module.css` e `ProgressIndicator.module.css` são removidos
(componentes absorvidos). `100dvh` com fallback `100vh` em todo lugar que hoje
usa só `vh`. `-webkit-backdrop-filter` junto de todo `backdrop-filter`.

## Acessibilidade

- O progresso: `aria-label="Passo N de T"` no container; cada círculo/segmento
  com estado (`aria-current="step"` no atual).
- `TrocaPasso`: a região do form com `aria-live="polite"` não — a troca é
  disparada por ação do usuário (clicar Próximo/Voltar), o foco deve ir pro
  primeiro campo da cena nova (ou pro `<h3>` dela). Definir foco após a
  animação (`setTimeout` ~ duração), só acima de 768px (no mobile o foco
  automático abre o teclado empurrando o layout — mesmo motivo do
  `ModalMinisterioForm`).
- `prefers-reduced-motion`: todas as animações (slide, altura, brilho, cascata,
  logo) viram instantâneas/estáticas.

## Casos de borda (mantêm o comportamento atual)

- **CNPJ_DUPLICADO** no `onSubmit`: `setError('cnpj')` + `setPasso(1)` →
  `direcaoPasso = -1`, volta pro passo 1 mostrando o erro no campo.
- **EMAIL_DUPLICADO**: `setError2('emailAdmin')`, fica no passo 2.
- **Erro geral**: `erroGeral` renderiza no rodapé da cena atual (como hoje).
- **Google no passo 1**: `googleData` setado → passo 1 re-renderiza no modo
  Google (banner + checkbox), `totalPassos` vira 2, o progresso re-desenha.
- **Voltar do passo 2 → 1**: `voltarParaPasso1`, `direcaoPasso = -1`. Os dados
  do passo 1 (`dataPasso1`) e do RHF continuam preenchidos.
- **Reload no meio**: o wizard não persiste estado (comportamento atual) —
  recarregar volta pro passo 1. Fora de escopo mudar isso.

## Arquivos

**Novos:**
- `src/app/(auth)/cadastro/CadastroShell.tsx` + `.module.css`
- `src/app/(auth)/cadastro/PainelWizard.tsx` + `.module.css`
- `src/app/(auth)/cadastro/TrocaPasso.tsx` + `.module.css`
- `src/app/(auth)/cadastro/Passo3.tsx` + `.module.css`

**Modificados:**
- `src/app/(auth)/cadastro/page.tsx` — fica fino
- `src/app/(auth)/cadastro/Passo1.tsx` + `.module.css` — vira miolo
- `src/app/(auth)/cadastro/Passo2.tsx` + `.module.css` — vira miolo
- `src/hooks/auth/UseRegistrarIgreja.ts` — `direcaoPasso` + `marcarBoasVindasVista()`

**Removidos:**
- `src/app/(auth)/cadastro/SanctuaryPanel.tsx` + `.module.css`
- `src/app/(auth)/cadastro/ProgressIndicator.tsx` + `.module.css`
- `src/app/(auth)/cadastro/Page.module.css` (conteúdo migra pros novos)

**Intocados:** `PasswordStrengthIndicator.*`, `SecurityFooter.*`, `lib/validators`,
`services/auth.service`, backend.

## Teste

Sem testes de frontend no projeto (dívida conhecida). Validação manual:

- Fluxo nativo completo: passo 1 → 2 → 3, ida e volta, transições suaves.
- Fluxo Google: passo 1 (modo Google) → 3, progresso "de 2".
- Erros: CNPJ duplicado (volta pro passo 1 com erro), e-mail duplicado (fica
  no 2), erro geral de API.
- Mobile (iPhone + Android): barra de progresso, transições, sem overflow
  horizontal, teclado não quebra o layout.
- `prefers-reduced-motion` ligado: sem animação, tudo funcional.
- Depois do passo 3: clicar um atalho leva pra rota certa; a animação "curta"
  de boas-vindas do login **não** toca no primeiro acesso ao app.
