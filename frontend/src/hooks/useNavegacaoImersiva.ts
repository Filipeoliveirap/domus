import { useCallback, useState } from 'react'
import { useRouter } from 'next/navigation'

/**
 * Navegação "imersiva" de card → página de detalhe: ao clicar, uma cópia do card nasce
 * no lugar exato dele (`position: fixed`) e cresce até ocupar a área de conteúdo, com o
 * conteúdo do card dissolvendo. A navegação acontece por baixo e a cópia some com fade —
 * a tela de detalhe (com o cabeçalho já vindo do cache da lista, ver `useCelula`) fica
 * exatamente onde a cópia estava. Dá a sensação de "abrir" o card, não de trocar de tela.
 *
 * É um FLIP puro (First-Last-Invert-Play) em CSS/JS — nada de View Transitions API nem
 * biblioteca. Roda igual em qualquer navegador e independe de a rota de destino já estar
 * carregada (o problema que derrubou a tentativa com View Transitions no dev).
 *
 * Fallback: `prefers-reduced-motion` → navega seco, sem animação.
 *
 * `saindoId` fica setado até a lista desmontar (a navegação leva o estado junto). Serve
 * pra escurecer os outros cards enquanto a cópia cresce (`.imersivo-navegando` no
 * globals.css).
 */
const CRESCER_MS = 440

function abrirOverlayFlip(cardEl: HTMLElement, aoMeio: () => void) {
  const cr = cardEl.getBoundingClientRect()
  // Alvo = a viewport inteira à DIREITA da sidebar (não o wrapper do conteúdo, que fica
  // recuado pelo padding do <main> e/ou mais curto que a tela → sobra "pedaço pra fora").
  const main = document.querySelector('main')
  const esquerda = main ? main.getBoundingClientRect().left : 0
  const tr = {
    left: esquerda,
    top: 0,
    width: window.innerWidth - esquerda,
    height: window.innerHeight,
  }

  const easing = 'cubic-bezier(0.22, 1, 0.36, 1)'
  const wrap = document.createElement('div')
  wrap.setAttribute('aria-hidden', 'true')
  wrap.style.cssText = [
    'position:fixed',
    'z-index:60',
    'overflow:hidden',
    'pointer-events:none',
    'box-sizing:border-box',
    'background:var(--color-bg-white,#fff)',
    'border:1px solid var(--color-border,#e5e7eb)',
    'border-radius:var(--radius-lg,16px)',
    'box-shadow:0 30px 80px -24px rgba(19,27,46,0.45)',
    `left:${cr.left}px`,
    `top:${cr.top}px`,
    `width:${cr.width}px`,
    `height:${cr.height}px`,
    `transition:left ${CRESCER_MS}ms ${easing},top ${CRESCER_MS}ms ${easing},` +
      `width ${CRESCER_MS}ms ${easing},height ${CRESCER_MS}ms ${easing},` +
      `border-radius ${CRESCER_MS}ms ease,box-shadow ${CRESCER_MS}ms ease`,
  ].join(';')

  const clone = cardEl.cloneNode(true) as HTMLElement
  clone.querySelectorAll('button, [role="button"]').forEach((n) => n.removeAttribute('role'))
  clone.style.cssText +=
    `;position:absolute;left:0;top:0;margin:0;width:${cr.width}px;height:${cr.height}px;` +
    'transform:none;transition:opacity 200ms ease,transform 440ms ' + easing + ';'
  wrap.appendChild(clone)
  document.body.appendChild(wrap)

  requestAnimationFrame(() => {
    wrap.style.left = `${tr.left}px`
    wrap.style.top = `${tr.top}px`
    wrap.style.width = `${tr.width}px`
    wrap.style.height = `${tr.height}px`
    wrap.style.borderRadius = '0'
    wrap.style.boxShadow = 'none'
    clone.style.opacity = '0'
    clone.style.transform = 'scale(1.04)'
  })

  // navega quando a cópia já cobre boa parte do caminho — a rota nova assenta por baixo
  setTimeout(aoMeio, CRESCER_MS * 0.55)
  // e some quando terminou de crescer, revelando a tela de detalhe já no lugar
  setTimeout(() => {
    wrap.style.transition = 'opacity 240ms ease'
    wrap.style.opacity = '0'
    setTimeout(() => wrap.remove(), 280)
  }, CRESCER_MS + 40)
}

export function useNavegacaoImersiva() {
  const router = useRouter()
  const [saindoId, setSaindoId] = useState<string | null>(null)

  // Prefetch da ROTA no hover (o card é um <div>, não <Link>). Assim, quando a cópia
  // termina de crescer, a tela de detalhe já renderizou por baixo e a troca é invisível.
  const prefetch = useCallback((href: string) => router.prefetch(href), [router])

  const entrar = useCallback(
    (href: string, id: string) => {
      if (saindoId) return

      const reduzMovimento =
        typeof window !== 'undefined' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      const cardEl =
        typeof document !== 'undefined'
          ? document.querySelector<HTMLElement>(`[data-imersivo-card="${id}"]`)
          : null

      if (reduzMovimento || !cardEl) {
        router.push(href)
        return
      }

      setSaindoId(id)
      abrirOverlayFlip(cardEl, () => router.push(href))
    },
    [router, saindoId]
  )

  return { saindoId, entrar, prefetch }
}
