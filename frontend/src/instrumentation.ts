import * as Sentry from '@sentry/nextjs'

// Inicializa o Sentry no servidor (Node) e no edge. Sem DSN = desligado (dev).
export async function register() {
    const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN
    if (!dsn) return

    Sentry.init({
        dsn,
        environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? 'dev',
        tracesSampleRate: 0,
        sendDefaultPii: false,
        // Ruído de stream SSE: cliente que desconecta (aba fechada, app em background,
        // rede móvel) ou o deploy recriando o container derrubam a conexão do
        // /api/notificacoes/stream. O EventSource reconecta sozinho — não é bug.
        ignoreErrors: [
            'failed to pipe response',
            'ResponseAborted',
            /^Error: aborted$/,
            'ERR_STREAM_PREMATURE_CLOSE',
        ],
    })
}

// Captura erros lançados dentro do runtime do Next (Server Components, rotas, etc.),
// exceto os de desconexão do stream de notificações (ver ignoreErrors acima).
export function onRequestError(
    ...args: Parameters<typeof Sentry.captureRequestError>
) {
    const [, request] = args
    if (typeof request?.path === 'string' && request.path.startsWith('/api/notificacoes/stream')) {
        return
    }
    return Sentry.captureRequestError(...args)
}
