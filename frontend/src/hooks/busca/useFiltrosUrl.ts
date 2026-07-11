'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter, useSearchParams, usePathname } from 'next/navigation'

export function useFiltrosUrl<T extends Record<string, string>>(iniciais: T) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const [filtros, setFiltros] = useState<T>(() => {
    const resultado = { ...iniciais }
    for (const chave of Object.keys(iniciais)) {
      const daUrl = searchParams.get(chave)
      if (daUrl !== null) {
        resultado[chave as keyof T] = daUrl as T[keyof T]
      }
    }
    return resultado
  })

  useEffect(() => {
    const params = new URLSearchParams()
    for (const [chave, valor] of Object.entries(filtros)) {
      if (valor) params.set(chave, valor)
    }
    const qs = params.toString()
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false })
  }, [filtros, pathname, router])

  const setFiltro = useCallback((chave: keyof T, valor: string) => {
    setFiltros((prev) => ({ ...prev, [chave]: valor }))
  }, [])

  return { filtros, setFiltro, setFiltros }
}