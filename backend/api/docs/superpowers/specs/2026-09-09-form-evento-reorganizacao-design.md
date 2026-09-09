# Reorganização do formulário de evento — design

> Status: aprovado no brainstorm (2026-09-09). Só front. Nenhuma mudança de
> backend, schema, contrato de API ou validação server-side.

## Problema

`EventoForm.tsx` cresceu com recorrência, campos personalizados, prazo de
inscrição, política de cancelamento, restrição por igreja e "para quem é". Hoje:

- Layout de 2 colunas no desktop (`grid-template-columns: 2fr 1fr`, coluna
  direita `sticky`) que colapsa pra 1 coluna no mobile — duas plataformas com
  layouts diferentes de manter, colunas de altura desigual, "muro de campos".
- Tudo aberto de uma vez, mesmo blocos que a maioria dos eventos não usa
  (recorrência, faixa etária/restrições, campos personalizados).
- Hint longo em quase todo campo.
- Dois toggles com nome que exige explicação: **"Requer inscrição prévia"** e
  **"Controlar presença"**.
- Botão salvar **travado** por `isFormIncomplete` (só `titulo`, `inicioData`,
  `inicioHora`) e por `precisaConectarContaPagamento`. O usuário não sabe qual
  campo falta.

## Objetivo

Uma tela, coluna única, **mesma estrutura no desktop e no mobile**. Caminho
simples (título + data) fica curto; o resto aparece só sob demanda. Botão
sempre ativo; ao submeter incompleto, a página **rola suave até o primeiro
campo com erro**, foca ele e abre o bloco que o contém. Mensagens curtas.
Prévia ao vivo do evento acima do botão.

## Não-objetivos

- Não é wizard. Não muda o fluxo de edição (mesmos 3 modais de impacto:
  restrição, mudança de preço, escopo de série).
- Não mexe em `useAppForm` nem no `isFormIncomplete` dos outros formulários
  (login, pessoa, movimentação, etc.) — só o `EventoForm` para de usar o gate.
- Não mexe em `BlocoParaQuemE` a fundo, `SeletorLocal`, `SeletorResponsavel`,
  `CamposPersonalizadosPainel`, `UploadFoto` — são reaproveitados como estão.
- Sem teste automatizado de front (não existe infra). Validação manual.

---

## Arquitetura

Quatro peças novas, todas em `frontend/src`:

### 1. `hooks/forms/useRolarParaErro.ts` — utilitário reusável

Hook genérico de "leva o usuário até o primeiro erro". Não conhece evento —
serve pra qualquer form depois (fora do escopo ligar nos outros agora).

```ts
export function useRolarParaErro(formRef: React.RefObject<HTMLFormElement | null>): {
  rolarParaErro: () => void
}
```

- `rolarParaErro()` é chamado no callback de **erro** do
  `handleSubmit(onValid, onInvalid)` do react-hook-form.
- Implementação:
  1. `requestAnimationFrame` (deixa o React pintar os `aria-invalid`/erros).
  2. Antes de procurar: abre todo bloco recolhível que contenha erro —
     `formRef.current.querySelectorAll('[data-recolhivel][hidden]')` cujo
     conteúdo tenha `[data-campo-erro]`; dispara a abertura via um evento
     custom `domus:abrir-recolhivel` que o `<BlocoRecolhivel>` escuta (evita
     acoplar o hook ao componente).
  3. `const alvo = formRef.current.querySelector('[data-campo-erro]')` — o
     primeiro na ordem do DOM (= ordem visual, coluna única).
  4. `alvo.scrollIntoView({ behavior, block: 'center' })` — `behavior: 'smooth'`
     exceto sob `prefers-reduced-motion` (`'auto'`).
  5. Sobe até o container focável do campo (`[data-campo-foco]` ou o próprio
     `input`/`button`) e `.focus({ preventScroll: true })`.
  6. Adiciona a classe `.tremido` (shake curto, 1 ciclo) e remove depois de
     `450ms`. Sob `prefers-reduced-motion`, não adiciona.
- Um `useRef` de "última contagem de tentativas" evita rolar de novo sem novo
  submit.

**Marcadores no DOM** (novos, adicionados nos campos do `EventoForm` e nos
componentes comuns quando fizer sentido):
- `data-campo-erro` — no elemento de **mensagem de erro** de cada campo. Já que
  vários campos usam o `<Input>` comum, o `<Input>` passa a marcar seu `<span>`
  de erro com `data-campo-erro`. `<CampoData>`, `textarea`, os segmentados e o
  `SeletorLocal` marcam o próprio span de erro. Regra: **todo lugar que hoje
  renderiza `errors.x?.message` ganha `data-campo-erro` no wrapper do texto.**
- `data-campo-foco` — opcional, no elemento que deve receber `focus()` quando o
  erro não está num `<input>` nativo (ex.: o primeiro `<button>` de um
  segmentado). Sem ele, o hook foca o primeiro control focável dentro do
  container do campo.

### 2. `components/common/BlocoRecolhivel/` — disclosure

Cabeçalho clicável + corpo que expande suave. Não existe equivalente hoje
(`Revelar` é controlado por booleano externo; aqui o estado é do próprio
componente).

```tsx
interface BlocoRecolhivelProps {
  titulo: string
  descricao?: string
  icone?: React.ReactNode
  /** Abre já expandido (ex.: edição de evento que já tem restrição ativa). */
  defaultAberto?: boolean
  /** id estável, usado no aria e no evento de "abrir por causa de erro". */
  id: string
  children: React.ReactNode
}
```

- Cabeçalho: `<button type="button">` com `aria-expanded` / `aria-controls`,
  chevron que gira 90°, título + descrição opcional.
- Corpo: `<div role="region" hidden={!aberto} data-recolhivel data-id={id}>`.
  Animação de altura pelo truque `grid-template-rows: 0fr → 1fr` (mesmo do
  `Revelar`), com `@media (prefers-reduced-motion: reduce)` zerando a
  transição. Usa `el.hidden`, não `display`.
- Escuta `window` por `domus:abrir-recolhivel` com `detail.id === props.id` →
  seta aberto. É como o `useRolarParaErro` força a abertura.
- `:active { transform: scale(0.995) }` no cabeçalho + reduced-motion.

### 3. `components/module/eventos/PreviaEvento.tsx` — prévia ao vivo

Puramente apresentacional. Recebe os valores já observados pelo `EventoForm`
(não faz `useWatch` próprio).

```tsx
interface PreviaEventoProps {
  titulo?: string
  tipo?: string
  inicioData?: string
  inicioHora?: string
  fimData?: string
  localResumo?: string        // nome do local / cidade / "—"
  fotoId?: string | null
  requerInscricao: boolean
  tipoInscricao: 'GRATUITO' | 'PAGO'
  preco?: string
  vagas?: number
  inscricoesAteData?: string
  exclusivoMembros: boolean
  idadeMin?: number
  idadeMax?: number
  restricaoEstadoCivil?: string | null
  restricaoSexo?: string | null
  restritoPropriaIgreja: boolean
  temFamilia: boolean
  controlaPresenca: boolean
  camposFaltando: string[]    // rótulos legíveis dos required ainda vazios
}
```

Renderiza:
- Mini-card: thumb da foto (via `GET /fotos/{id}?tamanho=thumb`) ou placeholder,
  título (ou "Sem título" em cinza), linha `<dia da semana abrev>, <dd mmm> ·
  <hh:mm> · <local>` — cada pedaço que falta some (não vira "undefined").
- Lista `<ul>` de regras em português, montada por uma função pura
  `resumirRegras(props): string[]`:
  - sem inscrição → `"Aberto a todos · sem inscrição"` (a menos que haja
    restrição de público — aí `"Sem inscrição"` + as linhas de restrição).
  - com inscrição → `"Inscrição obrigatória"`, e então:
    `vagas` → `"50 vagas"` (ou `"vagas ilimitadas"`);
    `inscricoesAteData` → `"inscrições até 14/03"`;
    `tipoInscricao === 'PAGO' && preco` → `"R$ 30,00"` (senão `"gratuito"`).
  - restrição de público → `"Só para membros"`, `"18–35 anos"`,
    `"somente mulheres"`, `"apenas solteiros(as)"` — uma linha cada, só as
    ativas.
  - `restritoPropriaIgreja && temFamilia` → `"Não aparece para as outras
    congregações"` (usar o rótulo dinâmico via `useRotulos`, igual ao toggle).
  - `controlaPresenca` → `"Check-in ativado"`.
  - `camposFaltando.length > 0` → **primeira** linha, classe `.faltando`
    (vermelha): `"Falta preencher: título e data de início"` (junta com
    vírgula / "e"). As outras linhas ficam em cinza normal.
- Sob `prefers-reduced-motion` nada anima; o bloco em si aparece via
  `<Transicao modo="fade">` na montagem (já é padrão do projeto) — mas como
  está sempre montado, na prática só as trocas de conteúdo, que são texto, sem
  animação.

### 4. `EventoForm.tsx` + `EventoForm.module.css` — reestrutura

Sem novos dados no form. Só reordena o JSX, troca textos, e troca o grid de 2
colunas por **coluna única `max-width: 720px` centralizada**. Remove
`.secaoData` (fundo creme) — todas as seções usam `.secao`. Remove o `.infoBox`
"O evento aparecerá na agenda…".

Ordem nova (de cima pra baixo):

| # | Seção (`<section class="secao">`) | Conteúdo |
|---|---|---|
| 1 | **Sobre o evento** (`FileText`) | título*, tipo (`InputComSugestoes`), descrição, imagem (`UploadFoto` banner) |
| 2 | **Quando e onde** (`CalendarClock`) | início* (data+hora), término (data+hora), `SeletorLocal`, `<BlocoRecolhivel titulo="Repetir este evento">` (só `!ehEdicao`) com todo o bloco de recorrência atual |
| 3 | **Organização** (`UserCog`) | `SeletorResponsavel`, toggle "Só minha igreja" (só se `temFamilia`) |
| 4 | `<BlocoRecolhivel titulo="Restringir quem pode participar" descricao="Idade, estado civil, sexo ou só membros">` | `BlocoParaQuemE` inteiro. `defaultAberto` quando qualquer restrição já vem preenchida (idade/estado civil/sexo/`exclusivoMembros`). |
| 5 | **Inscrições** (`Ticket`) | toggle **"Exigir inscrição"** → `Revelar` com: vagas, "Inscrições até" + política de cancelamento, segmentado Gratuito/Pago + preço + aviso de conta, toggle **"Check-in"**, `<BlocoRecolhivel titulo="Campos personalizados" descricao="Perguntas extras no formulário de inscrição">` com `CamposPersonalizadosPainel` |
| — | `<PreviaEvento>` | fora de `<section>`, colado acima das ações |
| — | `.acoes` | botão salvar (sempre ativo) + Cancelar |

Nota: hoje "Campos personalizados" fica **fora** do grid de 2 colunas (largura
inteira) de propósito. Com coluna única de 720px isso deixa de ser problema —
entra dentro da seção Inscrições, recolhido.

**Botão salvar:**
```tsx
disabled={isLoading || isVerificandoImpacto}   // só o que é "operação em curso"
```
Sai `isFormIncomplete` e `precisaConectarContaPagamento` do `disabled`.

**Submit incompleto:** o callback de erro do `handleSubmit` chama
`rolarParaErro()` e seta um banner curto:
`setErroValidacao('Faltou preencher um campo — te levei até ele.')`.

**Evento pago sem conta conectada:** deixa de travar o botão. No `onSubmit`
(client), antes de montar o payload, se `requerInscricao && tipoInscricao ===
'PAGO' && contaPagamento && !contaPagamento.conectada`:
`form.setError('preco', { type: 'manual', message: 'Conecte uma conta de
recebimento antes de publicar um evento pago.' })` + `rolarParaErro()` +
`return`. O aviso amarelo (`avisoContaPagamento`) ganha `data-campo-erro` pra
ser alvo do scroll. (O backend continua recusando de qualquer forma — isto é só
UX antecipada.)

---

## Textos — antes → depois

| Onde | Antes | Depois |
|---|---|---|
| toggle `requerInscricao` título | "Requer inscrição prévia" | **"Exigir inscrição"** |
| toggle `requerInscricao` descrição | "Ative para controlar vagas, preço e restrições de quem pode participar." | **"Participantes se inscrevem antes. Você controla vagas, prazo e valor."** |
| toggle `controlaPresenca` título | "Controlar presença" | **"Check-in"** |
| toggle `controlaPresenca` descrição | "Ative para marcar quem realmente compareceu e ver o relatório de presença deste evento." | **"Marque quem chegou no dia e veja quantos vieram."** |
| toggle `repetir` descrição | "Cadastre uma vez e as próximas ocorrências aparecem sozinhas." | **"As próximas datas entram na agenda sozinhas."** |
| caixa `infoBox` na seção de data | "O evento aparecerá na agenda da igreja assim que for salvo." | **(removida)** |
| hint "Inscrições até" | "Ex.: 15/03/2026 23:59 — deixe vazio pra aceitar inscrições até o evento começar." | **"Vazio = aceita inscrição até o evento começar."** |
| hint política cancelamento | "Antes do prazo, o cancelamento é sempre livre e com reembolso total." | **"Antes do prazo, cancelar é sempre livre e com reembolso."** |
| hint preço | "Cobrado automaticamente na inscrição, através da conta de recebimento conectada pela igreja." | **"Cobrado na hora da inscrição."** |
| hint vagas | "Deixe vazio para não limitar." | **"Vazio = sem limite de vagas."** |
| banner de erro de validação | "Alguns campos precisam de atenção — confira os destaques em vermelho." | **"Faltou preencher um campo — te levei até ele."** |
| toggle "Apenas minha igreja" título | "Apenas minha igreja" | **"Só minha igreja"** |
| toggle "Apenas minha igreja" descrição | "Ative para este evento não aparecer para os demais {congregações}." | **"Não mostra este evento para {as outras congregações}."** |

Rótulos de campo (`TÍTULO DO EVENTO*` etc.) e placeholders mantêm o padrão
atual (rótulo com exemplo no placeholder). O `*` de obrigatório continua.

---

## Fluxo de dados

Sem mudança de payload. `useEventoForm.onSubmit` monta o mesmo `EventoRequest`.
`controlaPresenca` continua forçado a `false` quando `!requerInscricao`
(lógica atual preservada). A prévia lê `watch(...)` dos mesmos campos que o
form já observa.

`camposFaltando` pra `<PreviaEvento>`: derivado no `EventoForm` a partir de
`errors` + valores observados dos required (`titulo`, `inicioData`,
`inicioHora`), mapeados pra rótulos legíveis (`{ titulo: 'título', inicioData:
'data de início', inicioHora: 'horário de início' }`). Só entra na lista o que
está vazio **e** (o form já foi submetido uma vez **ou** o campo foi tocado) —
não acusa erro antes da pessoa interagir.

---

## Tratamento de erro

- Erros de validação Zod: inalterados (mensagens já revisadas em sessão
  anterior). Ganham só o `data-campo-erro` no wrapper.
- `rolarParaErro` é tolerante: se não achar `[data-campo-erro]` (caso raro de
  erro só no `erroGeral` do servidor), não faz nada além do banner.
- `BlocoRecolhivel` que contém erro e está fechado: aberto antes do scroll.
- Modais de impacto (restrição / preço / escopo): disparados só no submit
  **válido**, exatamente como hoje — o botão sempre ativo não muda isso.

---

## Responsivo

- Coluna única já resolve a maior parte. `max-width: 720px; margin: 0 auto`.
- `@media (max-width: 767px)`: `.secao` com `padding: 18px`, labels menores,
  `linhaDataHora` empilha (já existe), `BlocoRecolhivel` cabeçalho com área de
  toque ≥ 44px.
- `PreviaEvento` no mobile: mini-card empilha thumb sobre texto se < 380px.
- Testar no viewport de iPhone e de Android (checklist abaixo).

---

## Validação manual (checklist)

Cadastro:
1. Só título → clica Salvar → rola até "data de início", foca, treme leve,
   banner aparece. Prévia mostra "Falta preencher: data e horário de início".
2. Preenche data/hora → prévia vira "Aberto a todos · sem inscrição" → salva OK.
3. "Repetir" fechado por padrão; abre, preenche semanal, salva série.
4. "Restringir quem pode participar" fechado; abre, marca "só membros" +
   18–35 → prévia lista as 2 linhas.
5. "Exigir inscrição" ON → vagas/prazo/tipo aparecem; "Check-in" visível só
   aqui; "Campos personalizados" recolhido dentro.
6. Pago sem conta conectada → Salvar → rola até o aviso amarelo, não trava.
7. `prefers-reduced-motion` on → sem shake, scroll instantâneo, sem animação
   de abertura de bloco.

Edição:
8. Evento com restrição ativa → bloco "Restringir…" abre já expandido.
9. Trocar horário e salvar → mesmo modal de escopo (se série) / impacto.
10. Mobile (iPhone + Android): sem scroll horizontal, cabeçalhos de bloco
    tocáveis, prévia legível.

Build:
11. `npm run build` limpa; `npm run lint` sem erro novo.

---

## Arquivos

**Novos**
- `frontend/src/hooks/forms/useRolarParaErro.ts`
- `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.tsx`
- `frontend/src/components/common/BlocoRecolhivel/BlocoRecolhivel.module.css`
- `frontend/src/components/module/eventos/PreviaEvento.tsx`
- `frontend/src/components/module/eventos/PreviaEvento.module.css`

**Modificados**
- `frontend/src/components/module/eventos/EventoForm.tsx` — reestrutura JSX,
  textos, remove `isFormIncomplete`/`precisaConectarContaPagamento` do
  `disabled`, liga `useRolarParaErro`, monta `camposFaltando`, renderiza
  `PreviaEvento`.
- `frontend/src/components/module/eventos/EventoForm.module.css` — coluna
  única, remove `.colunas`/`.colunaEsquerda`/`.colunaDireita`/`.secaoData`/
  `.infoBox`/`.infoIcon`/`.infoText`, adiciona `.tremido` (keyframes shake +
  reduced-motion).
- `frontend/src/components/common/input/Input.tsx` — `data-campo-erro` no span
  de erro.
- `frontend/src/components/common/CampoData/CampoData.tsx` — `data-campo-erro`
  no span de erro.
- `frontend/src/components/module/eventos/BlocoParaQuemE.tsx` — `data-campo-erro`
  no erro de idade máxima.
- (Se `SeletorLocal` renderiza erro próprio) `data-campo-erro` nele também.

**Sem tocar:** `useEventoForm.ts` (só liga o novo hook via `EventoForm`),
`useAppForm.ts`, `validators.ts`, qualquer coisa de backend, as páginas
wrapper `cadastrar/page.tsx` e `[id]/page.tsx` (o chrome/breadcrumb continua).

---

## Riscos

- **`data-campo-erro` incompleto** — se algum campo com erro não tiver o
  marcador, o scroll pula ele. Mitigação: a checklist 1/6 exercita os
  caminhos; e o `<Input>` comum cobre a maioria dos campos de uma vez.
- **`BlocoParaQuemE` tem segmentado "Todos/Faixa" próprio** — encapsular ele
  dentro de `BlocoRecolhivel` cria dois níveis de "abrir". Aceitável: o
  `BlocoRecolhivel` é "tem ou não tem restrição"; o segmentado interno é
  "faixa etária vs. campos individuais". `defaultAberto` alinhado com
  `temRestricaoAtiva` evita o pior caso (restrição ativa escondida).
- **Prévia com foto nova em cadastro** — `fotoId` só existe após upload; em
  cadastro o `UploadFoto` guarda o id assim que sobe. A thumb na prévia usa
  esse id — funciona. Sem foto, placeholder.
