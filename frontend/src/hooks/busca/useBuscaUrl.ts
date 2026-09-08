'use client'

import { useState, useEffect, useRef } from 'react'
import { useRouter, useSearchParams, usePathname } from 'next/navigation'
import { useDebounce } from '@/hooks/useDebounce'

interface UseBuscaUrlOptions {
  delay?: number
  param?: string
}

export function useBuscaUrl({ delay = 350, param = 'q' }: UseBuscaUrlOptions = {}) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const [busca, setBusca] = useState(searchParams.get(param) ?? '')
  const buscaDebounced = useDebounce(busca, delay)

  // Sincroniza QUANDO a URL muda por fora (ex.: busca global levou pra `/pessoas?q=João`
  // já estando em `/pessoas`) — antes disso a busca só era lida na montagem, então a lista
  // não reagia e a busca global precisava de um reload duro. Ajuste de estado no render
  // (padrão React), só adota o valor da URL quando ele difere do que já estamos por
  // escrever — assim não briga com o próprio efeito de escrita abaixo.
  const urlAtual = searchParams.get(param) ?? ''
  const [urlAnterior, setUrlAnterior] = useState(urlAtual)
  if (urlAtual !== urlAnterior) {
    setUrlAnterior(urlAtual)
    if (urlAtual !== buscaDebounced) setBusca(urlAtual)
  }

  const pathnameRef = useRef(pathname)
  const searchParamsRef = useRef(searchParams)

  useEffect(() => {
    pathnameRef.current = pathname
    searchParamsRef.current = searchParams
  }, [pathname, searchParams])

  useEffect(() => {
    const params = new URLSearchParams(searchParamsRef.current.toString())
    if (buscaDebounced) {
      params.set(param, buscaDebounced)
    } else {
      params.delete(param)
    }
    const qs = params.toString()
    router.replace(qs ? `${pathnameRef.current}?${qs}` : pathnameRef.current, { scroll: false })
  }, [buscaDebounced, param, router])

  return { busca, setBusca, buscaDebounced }
}