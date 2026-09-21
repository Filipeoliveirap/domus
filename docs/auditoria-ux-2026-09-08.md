# Auditoria de UI/UX do frontend — 2026-09-08

## Resumo

O frontend já está num patamar alto de acabamento: as listas principais (pessoas,
eventos, usuários, movimentações, relatório de engajamento, célula) usam `<Transicao>`
com `key` de filtro/página, `<EstadoVazio>` com ação, `<EstadoErro>` com retry e skeletons
dedicados por tela. Modais e drawers de domínio (`ModalCelulaForm`, `ModalMinisterioForm`,
`ModalConfirmacao`, `ModalDetalheInscrito`, `DrawerDetalhePessoa`, `ModalInscreverPessoas`)
já têm `useFecharAnimado`, trava de scroll, `grabber` + bottom-sheet em `@media
(max-width: 767px)`, `100dvh` e `-webkit-backdrop-filter`. O padrão novo desta rodada
(`<TrocaCena>`, `PonteParaCheckout`, `TransicaoRota`) está aplicado no checkout e na
confirmação de presença.

As lacunas se concentram em três lugares: (1) **telas de entrada** — Dashboard e Início —
que ficaram fora do tratamento das listas (skeleton→dados seco, vazios crus); (2)
**fluxos multi-passo que trocam de "cena" por `return`/`if` seco** — cadastro (auth),
`ModalInscreverPessoas`, `forgot-password`, abas do Balancete — exatamente o anti-padrão
que `<TrocaCena>` resolveu no `BotaoConfirmarPresenca`; (3) **saída dos formulários de
cadastro** (`router.back()` seco, sem cena de sucesso). Além disso, alguns resíduos de
consistência (Suspense fallbacks crus, dois modais artesanais, `100vh` solto).

## Achados priorizados

### P1 — alto impacto, esforço baixo/médio

**Dashboard: skeleton→dados seco e estados vazios crus**
· **Onde:** `src/app/(app)/dashboard/page.tsx:80-142` (fluxo: abrir Dashboard) ·
**Problema:** os cards de número trocam `<Skeleton>` por valor individualmente (bom), mas
as duas listas (`Movimentações recentes`, `Próximos eventos`) fazem `isLoading ?
<SkeletonLista/> : <ul>` — troca seca. E o vazio é `<p className={styles.vazio}>Nenhuma
movimentação registrada.</p>` / `Nenhum evento próximo.` — texto solto, sem ícone nem
ação, enquanto as listas do resto do app usam `<EstadoVazio>`. É a primeira tela logada. ·
**Sugestão:** envolver cada lista num `<Transicao modo="fade">` com `key` do estado
(loading/vazio/cheio); trocar os `<p>` por `<EstadoVazio>` com `acaoPrimaria` ("Nova
movimentação" / "Ver eventos"). · **Esforço:** P

**Início: mesmo problema, tela ainda mais visitada**
· **Onde:** `src/app/(app)/inicio/page.tsx:188-197` e `252-261` (fluxo: home) ·
**Problema:** `SkeletonLista`→conteúdo sem transição; vazios crus (`Nenhum evento próximo
por enquanto.`, `Nenhum aniversariante este mês.`). O restante da página (hero, versículo)
entra sem `<Transicao>`. · **Sugestão:** `<Transicao>` na trilha de eventos e na lista de
aniversariantes ao resolver; `<EstadoVazio icone={Calendar}/>` / `icone={Cake}` para os
vazios. · **Esforço:** P

**`forgot-password`: troca "formulário → e-mail enviado" seca**
· **Onde:** `src/app/(auth)/forgot-password/page.tsx:16-86` (fluxo: esqueci a senha) ·
**Problema:** já existe `key={enviado ? 'enviado' : 'form'}` no `.card` (força remonta),
mas a troca entre o form e a confirmação "E-mail enviado" acontece sem animação nenhuma —
pisca. É o mesmo formato do checkout Pix (QR → confirmado). · **Sugestão:** envolver o
conteúdo do card num `<TrocaCena cenaKey={enviado ? 'enviado' : 'form'}>` — crossfade +
altura animada (o bloco "enviado" é mais baixo). · **Esforço:** P

**Balancete: troca de aba seca + `EstadoErro` cru**
· **Onde:** `src/app/(app)/financeiro/relatorios/balancete/page.tsx:48-90` (fluxo:
financeiro → balancete → alternar Minha Igreja / Consolidado / Por congregação) ·
**Problema:** as abas renderizam por `{aba === 'X' && ...}` — conteúdo some/aparece seco a
cada clique. E o erro é `<p className={styles.erro}>Não foi possível carregar… <button>` —
inconsistente com `<EstadoErro>` usado em todo o resto (inclusive nas outras telas de
financeiro). Contraste direto: `eventos/relatorio/page.tsx:108` já usa `<Transicao
key={chaveRelatorio}>`. · **Sugestão:** `<Transicao key={aba} modo="fade">` em volta do
bloco de resultado; trocar o `<p>` por `<EstadoErro ... aoTentarNovamente>`. ·
**Esforço:** P

**Cadastro de pessoa / evento: saída seca do formulário, sem cena de sucesso**
· **Onde:** `src/hooks/pessoa/useCadastrarPessoa.ts:106,118` e
`src/hooks/evento/useEventoForm.ts:196,199` (fluxo: cadastrar pessoa/evento) ·
**Problema:** ao salvar, dispara `notificar.sucesso('… cadastrada!')` e `router.back()` no
mesmo tick — o formulário inteiro é cortado e a lista aparece seca. Não há transição de
saída nem confirmação visual de "o que acabou de acontecer" (o cadastro via Google, em
`app/(auth)/cadastro/page.tsx:59`, tem um passo 3 de boas-vindas justamente por isso). ·
**Sugestão:** trocar o `return` por uma cena de sucesso via `<TrocaCena>` no wrapper da
página de cadastro (form ↔ card "Pessoa cadastrada" com atalhos: "Ver na lista" / "Cadastrar
outra"), navegando só depois; no mínimo, um `<Transicao>` de saída antes do `router.push`.
· **Esforço:** M

**BuscaGlobal: navegação por reload duro + resultados sem transição**
· **Onde:** `src/components/layout/busca/BuscaGlobal.tsx:63-66,93` (fluxo: busca global no
topo) · **Problema:** o comentário na linha 63 admite "precisa de reload de verdade" — a
navegação para um resultado na mesma rota faz reload total (flash branco, perde o app
shell). Os resultados também aparecem sem `<Transicao>`, e o vazio é `<div
className={styles.vazio}>Nenhum resultado…`. · **Sugestão:** navegar via `router.push` +
`router.refresh()` (ou key na página de destino) em vez de reload; `<Transicao modo="subir">`
no painel de resultados; `<EstadoVazio>` compacto no lugar do `<div>`. · **Esforço:** M

### P2 — vale a pena, esforço médio

**`ModalInscreverPessoas`: troca de cenas internas por `return` seco**
· **Onde:** `src/components/module/eventos/ModalInscreverPessoas.tsx:315-594` (fluxo:
inscrever pessoas num evento, sobretudo pago) · **Problema:** o modal alterna entre lista,
"fila de pendências", "sem conta MP", "compartilhar link", "painel de uma pessoa (pago)" e
"lista com footer" através de vários `if (...) return (<div>...)` — cada troca é um corte
seco, dentro de um fluxo que envolve pagamento. É o mesmo anti-padrão que o
`BotaoConfirmarPresenca` resolveu adotando `<TrocaCena>`. · **Sugestão:** consolidar os
estados numa `cenaKey` e renderizar via `<TrocaCena renderCena={...}>` dentro do corpo do
modal — a altura acompanha (lista alta ↔ painel baixo) e nada remonta. · **Esforço:** M/G

**Cadastro (Google/auth): troca de passo 1 → 2 → 3 seca**
· **Onde:** `src/app/(auth)/cadastro/page.tsx:28-165` (fluxo: cadastrar igreja) ·
**Problema:** cada passo é um `if (passo === N) return (...)` — o wizard troca de tela sem
transição, incluindo a chegada no passo 3 (boas-vindas), que é o momento mais importante
do onboarding. · **Sugestão:** `<TrocaCena cenaKey={String(passo)}>` no container do card;
passos deslizam em vez de piscar. · **Esforço:** M

**`ModalAniversariantes`: modal artesanal sem saída animada nem bottom-sheet**
· **Onde:** `src/app/(app)/inicio/page.tsx:79-125` + `inicio.module.css:442` (fluxo: Início
→ "Ver todos os N aniversariantes") · **Problema:** é um `<div className={styles.overlay}
onMouseDown={aoFechar}>` escrito à mão — sem `useFecharAnimado` (fecha seco), sem
`grabber`/bottom-sheet no mobile, e `max-height: min(600px, calc(100vh - 32px))` usa
`100vh` (não `dvh`). Os modais de domínio (`ModalMinisterioForm`, `ModalLocalForm`) já têm
tudo isso. · **Sugestão:** migrar para o mesmo esqueleto (`useFecharAnimado` + classe
`.saindo`, `@media (max-width: 767px)` bottom-sheet, `100dvh`). · **Esforço:** M

**`router.back()` pós-cadastro é frágil para deep-link**
· **Onde:** `useCadastrarPessoa.ts:118`, `useEventoForm.ts:199` · **Problema:** quem chega
em `/pessoas/cadastrar` por link direto (e-mail, atalho de onboarding) e salva cai em
`router.back()` sem histórico coerente. · **Sugestão:** `router.push('/pessoas')` /
`'/eventos'` explícito (combina com a cena de sucesso do P1). · **Esforço:** P

**Suspense fallbacks crus em rotas de lista**
· **Onde:** `pessoas/(lista)/page.tsx:270`, `eventos/(lista)/page.tsx:228`,
`financeiro/movimentacoes/(lista)/page.tsx:344`, `usuarios/page.tsx` (fluxo: primeira carga
de cada lista) · **Problema:** o `fallback` do `<Suspense>` de `useSearchParams` é `<div>
Carregando…</div>` / `<div className={styles.pagina}>Carregando…</div>` — texto solto que
pode piscar antes do skeleton de `!hidratado`. · **Sugestão:** reutilizar o `Skeleton*` da
própria tela como fallback do Suspense. · **Esforço:** P

### P3 — nice-to-have / consistência

**`backdrop-filter` sem prefixo `-webkit-`** · **Onde:**
`src/components/module/eventos/EventoCard.module.css`,
`src/components/layout/notificacoes/SinoNotificacoes.module.css` · **Problema:** todos os
outros ~30 arquivos pareiam `-webkit-backdrop-filter`; nesses dois o blur não aplica no
iOS Safari. · **Sugestão:** adicionar a linha `-webkit-` junto. · **Esforço:** P

**`100vh` solto onde deveria ser `100dvh`** · **Onde:** `components/layout/Sidebar.module.css:6`
(`height: 100vh`), `components/common/UploadFoto/CropperFoto.module.css:228`,
`app/(app)/inicio/ModalEventoResumo.module.css:22`, `inicio.module.css:442` · **Problema:**
no mobile com a barra de URL visível, o rodapé da sidebar e o cropper de foto cortam. ·
**Sugestão:** `100dvh` (mantendo `100vh` como fallback na linha anterior). · **Esforço:** P

**`ModalMinisterioForm` com `autoFocus` no input** · **Onde:**
`src/app/(app)/ministerios/(lista)/ModalMinisterioForm.tsx:96` · **Problema:** no
bottom-sheet mobile o `autoFocus` abre o teclado na hora e empurra o sheet para cima antes
da animação de entrada assentar. · **Sugestão:** focar só acima de 768px, ou após o
timeout da animação de entrada. · **Esforço:** P

**Login pisca o formulário antes do redirect de sessão ativa** · **Onde:**
`src/app/(auth)/login/page.tsx:30-37` · **Problema:** `authService.me()` roda no mount e
redireciona quem já tem cookie; enquanto isso o formulário completo é renderizado e some. ·
**Sugestão:** um véu curto (`<OverlayCarregando>` ou o mesmo padrão do `PonteParaCheckout`)
enquanto a checagem de `me()` não resolve. · **Esforço:** P

**Consistência de estado vazio** · **Onde:** `ModalInscreverPessoas.tsx:456-458`
("Nenhuma pessoa encontrada." como `<p className={styles.estado}>`), dashboard, início,
BuscaGlobal · **Problema:** metade do app usa `<EstadoVazio>` (com ícone, mensagem
orientadora e ação), a outra metade usa `<p>` cru. · **Sugestão:** padronizar em
`<EstadoVazio>` (ou uma variante `compacta` para dentro de modal). · **Esforço:** M

**Consistência de erro** · **Onde:** `balancete/page.tsx:66`, `financeiro/relatorios`
(`PaginaCarregando`/erro inline) vs. `<EstadoErro>` no resto · **Problema:** dois estilos
de "não foi possível carregar" coexistindo. · **Sugestão:** `<EstadoErro>` em todo lugar. ·
**Esforço:** P

## Fora de escopo / already good

- **Listas principais** (pessoas, eventos, usuários, movimentações, relatório de
  engajamento): `<Transicao key>` + `tabelaAtualizando` (opacity no refetch sem remontar) +
  `<EstadoVazio>`/`<EstadoErro>` + skeleton dedicado — é o padrão de referência, não mexer.
- **`celulas/[id]` remoção de membro**: linha colapsa animada, sem toast de sucesso (o
  resultado já é visível) — exatamente a régua do `CLAUDE.md`.
- **`ModalConfirmacao`, `ModalDetalheInscrito`, `ModalCelulaForm`, `ModalMinisterioForm`,
  `DrawerDetalhePessoa`**: `useFecharAnimado` + bottom-sheet `@media (max-width: 767px)` +
  `grabber` + `100dvh` + `env(safe-area-inset-bottom)` + `-webkit-backdrop-filter` — completos.
- **Checkout de pagamento e `BotaoConfirmarPresenca`**: `<TrocaCena>` + `PonteParaCheckout`
  + `loading.tsx` de rota — é o alvo de qualidade, já atingido nessa área.
- **`prefers-reduced-motion`**: presente em 58 arquivos CSS — cobertura boa.
- **`EventoForm`**: uso extensivo e correto de `<Revelar>` e `<Transicao modo="subir">` nos
  blocos condicionais de recorrência/inscrição/preço.
- **Toasts**: o projeto já evita `notificar.sucesso` redundante nas ações cujo resultado
  aparece na tela (remoção de membro, promoção a líder, marcar presença individual); os
  `notificar.sucesso` restantes nos hooks são de ações sem feedback visual imediato
  (arquivar, restaurar, reenviar convite, salvar config) — corretos.
