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
        // Ruído não acionável:
        // - promise rejeitada sem valor (ex.: fetch cancelado pelo react-query ao trocar de tela)
        // - o SDK do Mercado Pago tenta buscar o script de antifraude em www.mercadolibre.com;
        //   ad-blocker / proteção contra rastreamento do navegador (comum no Firefox) bloqueia,
        //   o SDK não trata, e vira unhandledrejection. O pagamento em si continua funcionando.
        ignoreErrors: [
            'Non-Error promise rejection captured',
            /NetworkError when attempting to fetch resource/,
            /Failed to fetch/,
            'mercadolibre.com',
        ],
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
