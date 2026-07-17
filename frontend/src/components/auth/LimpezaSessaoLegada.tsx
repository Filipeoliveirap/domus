'use client'

import { useEffect } from 'react'

/**
 * Limpeza única da migração para cookie httpOnly (2026-07-16).
 *
 * Antes, a sessão era guardada no `localStorage` (`domus:token` e a chave `domus:auth` do
 * zustand persist, que continha o **refresh token** — válido por 7 dias no servidor) e num
 * cookie setado por JS. Nada disso é lido mais, mas continuaria apodrecendo na máquina de
 * quem já usou o sistema.
 *
 * Fica no root layout, e não no AuthGuard, de propósito: o AuthGuard só monta DEPOIS de um
 * login bem-sucedido, e quem mais precisa da limpeza é justamente quem abre o app e não
 * loga — essa pessoa ficaria com o refresh token legível por XSS até ele expirar.
 *
 * Pode ser removido quando não houver mais chance de alguém ter as chaves antigas.
 */
export function LimpezaSessaoLegada() {
  useEffect(() => {
    localStorage.removeItem('domus:token')
    localStorage.removeItem('domus:auth')
    document.cookie = 'domus:token=; path=/; max-age=0'
  }, [])

  return null
}
