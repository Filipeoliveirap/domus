import { NextResponse } from 'next/server'

/**
 * A decisão de sessão saiu daqui.
 *
 * Este middleware checava a PRESENÇA de um cookie que qualquer JS podia forjar
 * (`document.cookie = 'domus:token=banana'` passava) — nunca foi um porteiro, era conforto
 * visual. O porteiro sempre foi o backend.
 *
 * Além de inútil, hoje quebraria: o `domus_access` dura 10 minutos reais, então ficar idle
 * e dar F5 chutaria o usuário para o `/login` com a sessão válida. E ele não tem como olhar
 * o `domus_refresh` (7 dias), porque o `Path=/api/auth` faz o navegador não enviá-lo numa
 * requisição de página.
 *
 * Quem decide no cliente agora é o `AuthGuard` + `GET /auth/me`, que é a verdade real.
 */
export function proxy() {
  return NextResponse.next()
}

export const config = {
  matcher: [],
}
