'use client'

import { useState, useEffect, useRef, useCallback } from 'react'
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

  const pathnameRef = useRef(pathname)
  const searchParamsRef = useRef(searchParams)

  useEffect(() => {
    pathnameRef.current = pathname
    searchParamsRef.current = searchParams
  }, [pathname, searchParams])

  useEffect(() => {
    const params = new URLSearchParams(searchParamsRef.current.toString())
    for (const [chave, valor] of Object.entries(filtros)) {
      if (valor) params.set(chave, valor)
      else params.delete(chave)
    }
    const qs = params.toString()
    router.replace(qs ? `${pathnameRef.current}?${qs}` : pathnameRef.current, { scroll: false })
  }, [filtros, router])

  const setFiltro = useCallback((chave: keyof T, valor: string) => {
    setFiltros((prev) => ({ ...prev, [chave]: valor }))
  }, [])

  return { filtros, setFiltro, setFiltros }
}