import type { Viewport } from 'next'
import { Inter, Geist } from 'next/font/google'
import { Providers } from '@/components/common/Providers'
import '@/styles/globals.css'
import { Toaster } from 'sonner'
import { cn } from "@/lib/utils";

// viewportFit: 'cover' é o que habilita `env(safe-area-inset-*)` no CSS — sem isso o header
// fixo fica atrás da barra de status / Dynamic Island no iPhone (Chrome no iOS = motor Safari).
export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  viewportFit: 'cover',
}

// Todo o app passa a renderizar por requisição (não mais pré-fabricado no build) — é o que
// permite o nonce da CSP (src/proxy.ts) chegar em toda página, em vez de só nas poucas
// rotas que já eram dinâmicas. Custo: performance de servir HTML pronto do cache; ganho:
// CSP com 'strict-dynamic' de verdade em vez de 'unsafe-inline' liberado geral.
export const dynamic = 'force-dynamic'

const geist = Geist({subsets:['latin'],variable:'--font-sans'});

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  weight: ['400', '500', '600', '700', '800'],
})

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="pt-BR" data-scroll-behavior="smooth" className={cn(inter.variable, geist.variable)}>
      <body>
        <Providers>
          {children}
          {/*
            Sem `richColors`: a aparência dos toasts é nossa, definida em
            components/common/Notificacao. O sonner fica só com a mecânica
            (fila, timers, acessibilidade).
          */}
          <Toaster
            position="top-right"
            offset={24}
            mobileOffset={0}
            gap={12}
            toastOptions={{ unstyled: true, classNames: { toast: 'w-full' } }}
          />
        </Providers>
      </body>
    </html>
  )
}