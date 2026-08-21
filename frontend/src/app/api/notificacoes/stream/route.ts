import { NextRequest } from 'next/server'
import http from 'node:http'

const apiInternalUrl = process.env.API_INTERNAL_URL ?? 'http://localhost:8080'

// Sem isto, o Next tenta otimizar a rota como estática/bufferizada e nunca chega a
// entregar bytes ao cliente enquanto o stream (que não termina) não fecha.
export const dynamic = 'force-dynamic'

// A rewrite genérica de next.config.ts (/api/:path* -> backend) não serve pra SSE, e nem
// dá pra usar o fetch() global aqui: o Next intercepta fetch() dentro de Route Handlers pra
// instrumentar cache/log, e em dev isso espera o corpo inteiro terminar antes de resolver —
// que nunca acontece com um SseEmitter(0L). Node http.request puro não passa por essa
// instrumentação e repassa o corpo como stream de verdade, mantendo o cookie httpOnly
// (mesma origem) e sem CORS.
export async function GET(request: NextRequest) {
  const destino = new URL('/notificacoes/stream', apiInternalUrl)

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
                res.on('data', (chunk) => controller.enqueue(chunk))
                res.on('end', () => controller.close())
                res.on('error', (err) => controller.error(err))
              },
              cancel() {
                res.destroy()
              },
            }),
          })
        },
      )
      req.on('error', reject)
      request.signal.addEventListener('abort', () => req.destroy())
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
