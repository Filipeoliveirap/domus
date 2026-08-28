import { NextRequest } from 'next/server'
import http from 'node:http'

const apiInternalUrl = process.env.API_INTERNAL_URL ?? 'http://localhost:8080'

// Sem isto, o Next tenta otimizar a rota como estática/bufferizada e nunca chega a
// entregar bytes ao cliente enquanto o stream (que não termina) não fecha.
export const dynamic = 'force-dynamic'

// Erros de socket que NÃO são bug: o cliente (aba fechada, app em background, rede móvel
// oscilando) ou o próprio deploy (container recriado) derrubam a conexão SSE. O EventSource
// do navegador reconecta sozinho. Tratar como erro só polui o Sentry.
function ehDesconexaoNormal(err: unknown): boolean {
  const e = err as { code?: string; message?: string }
  return (
    e?.code === 'ECONNRESET' ||
    e?.code === 'ERR_STREAM_PREMATURE_CLOSE' ||
    e?.message === 'aborted' ||
    e?.message?.includes('aborted') === true
  )
}

// A rewrite genérica de next.config.ts (/api/:path* -> backend) não serve pra SSE, e nem
// dá pra usar o fetch() global aqui: o Next intercepta fetch() dentro de Route Handlers pra
// instrumentar cache/log, e em dev isso espera o corpo inteiro terminar antes de resolver —
// que nunca acontece com um SseEmitter(0L). Node http.request puro não passa por essa
// instrumentação e repassa o corpo como stream de verdade, mantendo o cookie httpOnly
// (mesma origem) e sem CORS.
export async function GET(request: NextRequest) {
  const destino = new URL('/notificacoes/stream', apiInternalUrl)

  // true assim que o cliente desconecta — a partir daí qualquer erro no socket é esperado.
  let clienteDesconectou = false

  const { status, body } = await new Promise<{ status: number; body: ReadableStream<Uint8Array> }>(
    (resolve, reject) => {
      const req = http.request(
        destino,
        { headers: { Cookie: request.headers.get('cookie') ?? '' } },
        (res) => {
          resolve({
            status: res.statusCode ?? 502,
            body: new ReadableStream({
              start(controller) {
                res.on('data', (chunk) => {
                  try {
                    controller.enqueue(chunk)
                  } catch {
                    // controller já fechado (cliente saiu no meio do enqueue) — nada a fazer.
                  }
                })
                res.on('end', () => {
                  try { controller.close() } catch { /* já fechado */ }
                })
                res.on('error', (err) => {
                  // Desconexão normal → fecha limpo; nunca propaga como erro (evita o
                  // "failed to pipe response" que o Sentry marcava como high priority).
                  if (clienteDesconectou || ehDesconexaoNormal(err)) {
                    try { controller.close() } catch { /* já fechado */ }
                  } else {
                    try { controller.error(err) } catch { /* já fechado */ }
                  }
                })
              },
              cancel() {
                res.destroy()
              },
            }),
          })
        },
      )
      // Depois de resolvido o promise, um 'error' aqui é só o socket morrendo na
      // desconexão — engole. Antes de resolver, é falha real de conexão com o backend.
      req.on('error', (err) => {
        if (clienteDesconectou || ehDesconexaoNormal(err)) return
        reject(err)
      })
      request.signal.addEventListener('abort', () => {
        clienteDesconectou = true
        req.destroy()
      })
      req.end()
    },
  )

  return new Response(body, {
    status,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    },
  })
}
