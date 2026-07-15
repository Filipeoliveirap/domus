'use client'

import { GoogleOAuthProvider } from '@react-oauth/google'

/**
 * Envolve todas as telas de autenticação (login, cadastro, forgot/reset) com o provider do
 * Google, que disponibiliza o botão "Entrar com Google" e a entrega do ID token. O Client ID
 * é público (aparece no navegador), por isso vem de uma env NEXT_PUBLIC_.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID as string
  return <GoogleOAuthProvider clientId={clientId}>{children}</GoogleOAuthProvider>
}
