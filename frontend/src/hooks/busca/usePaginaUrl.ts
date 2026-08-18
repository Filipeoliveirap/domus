'use client'

import { useState, useEffect, useRef } from 'react'
import { useRouter, useSearchParams, usePathname } from 'next/navigation'

interface UsePaginaUrlOptions {
  param?: string
}

/**
 * Espelha {@link useBuscaUrl}: guarda a página atual (0-indexada) na URL. Sem isso, voltar
 * de uma edição pra lista (router.back()) recarrega a página como componente novo — o
 * useState local reseta pra 0 mesmo a URL preservando a busca, porque a paginação nunca
 * tinha ido pra URL.
 *
 * A sincronização roda num useEffect (nunca dentro do updater do setState) — chamar
 * router.replace ali dispararia "Cannot update Router while rendering" (React chama o
 * updater síncrono durante o render).
 */
export function usePaginaUrl({ param = 'page' }: UsePaginaUrlOptions = {}) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const [pagina, setPagina] = useState(() => {
    const valor = Number(searchParams.get(param))
    return Number.isInteger(valor) && valor > 0 ? valor : 0
  })

  const pathnameRef = useRef(pathname)
  const searchParamsRef = useRef(searchParams)

  useEffect(() => {
    pathnameRef.current = pathname
    searchParamsRef.current = searchParams
  }, [pathname, searchParams])

  useEffect(() => {
    const params = new URLSearchParams(searchParamsRef.current.toString())
    if (pagina > 0) {
      params.set(param, String(pagina))
    } else {
      params.delete(param)
    }
    const qs = params.toString()
    router.replace(qs ? `${pathnameRef.current}?${qs}` : pathnameRef.current, { scroll: false })
  }, [pagina, param, router])

  return { pagina, setPagina }
}
