import { Inter, Geist } from 'next/font/google'
import { Providers } from '@/components/common/Providers'
import '@/styles/globals.css'
import { Toaster } from 'sonner'
import { cn } from "@/lib/utils";

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
    <html lang="pt-BR" className={cn(inter.variable, geist.variable)}>
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
            gap={12}
            toastOptions={{ unstyled: true, classNames: { toast: 'w-full' } }}
          />
        </Providers>
      </body>
    </html>
  )
}