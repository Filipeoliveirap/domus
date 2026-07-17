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
    })
}

// Captura erros lançados dentro do runtime do Next (Server Components, rotas, etc.).
export const onRequestError = Sentry.captureRequestError
