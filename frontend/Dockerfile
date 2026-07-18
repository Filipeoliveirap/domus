# ---- build ----
FROM node:22-alpine AS build
WORKDIR /app

# NEXT_PUBLIC_* são ASSADOS no build (vão pro bundle do navegador). Nenhum é segredo:
# /api é caminho same-origin, o Google Client ID é público por design, o Sentry DSN é send-only.
ARG NEXT_PUBLIC_API_URL=/api
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID=
ARG NEXT_PUBLIC_SENTRY_DSN=
ARG NEXT_PUBLIC_SENTRY_ENVIRONMENT=prod
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    NEXT_PUBLIC_SENTRY_DSN=$NEXT_PUBLIC_SENTRY_DSN \
    NEXT_PUBLIC_SENTRY_ENVIRONMENT=$NEXT_PUBLIC_SENTRY_ENVIRONMENT

# O destino do rewrites() /api/* → Spring é ASSADO no build (modo standalone: os
# rewrites viram manifesto estático; a env NÃO é lida em runtime). Por isso precisa
# estar presente aqui, no `next build`, e não só no estágio de runtime.
ARG API_INTERNAL_URL=http://api:8080
ENV API_INTERNAL_URL=$API_INTERNAL_URL

COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- runtime ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
# Redundante com o ARG do build (o rewrites() já foi assado), mas mantido para que
# qualquer leitura server-side de API_INTERNAL_URL em runtime também aponte pro Spring.
ENV API_INTERNAL_URL=http://api:8080

# O standalone traz o server.js + só as deps usadas.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
