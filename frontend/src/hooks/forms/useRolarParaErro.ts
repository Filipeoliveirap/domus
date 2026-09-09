import { useCallback } from 'react'

/**
 * Leva o usuário até o primeiro campo com erro depois de um submit inválido:
 * abre qualquer <BlocoRecolhivel> fechado que contenha erro, rola suave até o
 * primeiro [data-campo-erro] (= primeiro no DOM = primeiro na ordem visual, já
 * que o form é coluna única), foca o control e dá um "tremido" curto.
 *
 * Genérico — não conhece o form de evento. Ligue no callback de erro do
 * handleSubmit: handleSubmit(onValid, () => { rolarParaErro(); ...banner }).
 * `formRef` aceita qualquer elemento container (form, div) que envolva os campos.
 */
export function useRolarParaErro(formRef: React.RefObject<HTMLElement | null>) {
  const rolarParaErro = useCallback(() => {
    const form = formRef.current
    if (!form) return

    const reduzMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches

    // Deixa o React pintar os aria-invalid / mensagens de erro antes de procurar.
    requestAnimationFrame(() => {
      // 1. Abre blocos recolhíveis fechados que contenham erro.
      form.querySelectorAll<HTMLElement>('[data-recolhivel][data-fechado]').forEach((bloco) => {
        if (bloco.querySelector('[data-campo-erro]')) {
          const id = bloco.getAttribute('data-id')
          if (id) window.dispatchEvent(new CustomEvent('domus:abrir-recolhivel', { detail: { id } }))
        }
      })

      // 2. Segunda rAF: o bloco já abriu, agora o alvo existe no layout.
      requestAnimationFrame(() => {
        const erro = form.querySelector<HTMLElement>('[data-campo-erro]')
        if (!erro) return

        // Container do campo: sobe até achar algo com [data-campo-foco] ou o pai
        // mais próximo que tenha um control focável.
        const container =
          erro.closest<HTMLElement>('[data-campo-foco]') ??
          erro.closest<HTMLElement>('label, .campo, section') ??
          erro.parentElement ??
          erro

        container.scrollIntoView({
          behavior: reduzMovimento ? 'auto' : 'smooth',
          block: 'center',
        })

        const focavel = container.querySelector<HTMLElement>(
          'input:not([type="hidden"]), textarea, select, button, [tabindex]',
        )
        focavel?.focus({ preventScroll: true })

        if (!reduzMovimento) {
          const alvoTremido = focavel ?? container
          alvoTremido.classList.add('tremido')
          window.setTimeout(() => alvoTremido.classList.remove('tremido'), 450)
        }
      })
    })
  }, [formRef])

  return { rolarParaErro }
}
