import type { NextConfig } from "next";
import { withSentryConfig } from "@sentry/nextjs";

// Destino real do Spring. Env SERVER-SIDE (sem NEXT_PUBLIC_): só o servidor do Next a lê,
// para montar o rewrite. O navegador nunca fala com a API direto.
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";

// CSP pragmática: libera o Google Identity (botão de login/cadastro) e restringe as origens.
// unsafe-inline/unsafe-eval são concessão ao Next.js sem CSP baseada em nonce (ver BACKLOG).
const csp = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://accounts.google.com https://accounts.google.com/gsi/client",
  "style-src 'self' 'unsafe-inline' https://accounts.google.com/gsi/style",
  "frame-src https://accounts.google.com",
  // A API é same-origin agora (via rewrite /api/*), então 'self' basta.
  "connect-src 'self' https://accounts.google.com https://*.sentry.io https://viacep.com.br",
  // `blob:` é a PRÉVIA LOCAL do upload: URL.createObjectURL() produz uma blob: URL para
  // mostrar a foto escolhida antes de enviar. Sem isto o navegador bloqueia e a pessoa vê
  // só o texto alternativo — a imagem "não aparece" sem erro visível na tela.
  // Continua não havendo domínio externo: a foto salva é servida pelo próprio Domus.
  "img-src 'self' data: blob: https://*.googleusercontent.com https://accounts.google.com",
  "font-src 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "object-src 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  // Empacota um servidor mínimo (sem node_modules inteiro) — imagem Docker enxuta (~150MB).
  output: "standalone",
  // O front chama /api/* na PRÓPRIA origem e o Next repassa pro Spring. Assim o cookie de
  // sessão é sempre first-party (SameSite=Lax) independente de onde a API for hospedada —
  // e a decisão de hospedagem sai do caminho crítico. Custo: um salto de rede a mais.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiInternalUrl}/:path*` },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
};

// withSentryConfig instrumenta o build. Upload de source maps só acontece com
// SENTRY_AUTH_TOKEN (fica pra prod/CI; ver BACKLOG). Sem token, apenas segue o build.
export default withSentryConfig(nextConfig, {
  org: process.env.SENTRY_ORG,
  project: process.env.SENTRY_PROJECT,
  authToken: process.env.SENTRY_AUTH_TOKEN,
  silent: !process.env.CI,
});
