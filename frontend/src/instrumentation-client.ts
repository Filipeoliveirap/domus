import * as Sentry from '@sentry/nextjs'

// Sentry do lado do navegador (erros de cliente). Sem DSN = desligado (dev).
const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN

if (dsn) {
    Sentry.init({
        dsn,
        environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? 'dev',
        // Sem APM/performance nem session replay (respeita o tier gratuito).
        tracesSampleRate: 0,
        // Não enviar PII automaticamente (IP, etc.) — LGPD.
        sendDefaultPii: false,
        beforeSend(event) {
            // Reforço de scrubbing: nunca vazar credenciais/sessão para terceiro.
            if (event.request?.headers) {
                delete event.request.headers['Authorization']
                delete event.request.headers['Cookie']
            }
            if (event.request) delete event.request.cookies
            return event
        },
    })
}

// Instrumenta transições de rota do App Router (necessário no @sentry/nextjs v9+).
export const onRouterTransitionStart = Sentry.captureRouterTransitionStart
