import { NextRequest, NextResponse } from 'next/server'

// CSP com nonce, aplicada no app inteiro: 'strict-dynamic' faz o navegador ignorar 'self' e
// 'unsafe-inline' pra script e exigir nonce em todo <script> — só funciona porque o app
// inteiro renderiza por requisição agora (ver `export const dynamic` no layout raiz), então
// o Next consegue carimbar o nonce certo em cada script que ele mesmo gera, em toda página.
// 'strict-dynamic' também deixa um script confiável (com nonce) carregar outros scripts sem
// precisarem de nonce próprio — é o que mantém o Google Identity (accounts.google.com/gsi/client)
// funcionando, mesmo ele injetando script dele mesmo na página.
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64')
  const isDev = process.env.NODE_ENV !== 'production'

  const csp = [
    "default-src 'self'",
    // 'unsafe-eval' só em dev: o React usa eval() de propósito em desenvolvimento (Fast
    // Refresh, reconstrução de stack trace) — nunca em produção, confirmado sem uso de
    // eval()/new Function() no bundle de produção (ver BACKLOG). Sem isso, `npm run dev`
    // trava com "eval() is not supported" no Console.
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ''} https://accounts.google.com https://accounts.google.com/gsi/client`,
    "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style",
    'frame-src https://accounts.google.com',
    // ws://localhost só em dev: é o websocket do Hot Module Reload (webpack-hmr) — sem
    // isso o navegador bloqueia a conexão e o Fast Refresh para de funcionar.
    `connect-src 'self' https://accounts.google.com https://*.sentry.io https://viacep.com.br${isDev ? ' ws://localhost:*' : ''}`,
    "img-src 'self' data: blob: https://*.googleusercontent.com https://accounts.google.com",
    "font-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "object-src 'none'",
  ].join('; ')

  const requestHeaders = new Headers(request.headers)
  requestHeaders.set('x-nonce', nonce)
  requestHeaders.set('Content-Security-Policy', csp)

  const response = NextResponse.next({ request: { headers: requestHeaders } })
  response.headers.set('Content-Security-Policy', csp)
  return response
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
}
