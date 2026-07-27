'use client'

import { useEffect } from 'react'

export function LimpezaSessaoLegada() {
  useEffect(() => {
    localStorage.removeItem('domus:token')
    localStorage.removeItem('domus:auth')
    document.cookie = 'domus:token=; path=/; max-age=0'
  }, [])

  return null
}
