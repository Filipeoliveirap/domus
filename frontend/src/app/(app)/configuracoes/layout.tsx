'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import styles from './configuracoes.module.css'

const ABAS = [
  { href: '/configuracoes/igreja', label: 'Dados da Igreja' },
  { href: '/configuracoes/igrejas-vinculadas', label: 'Igrejas Vinculadas' },
]

/**
 * As abas vivem no layout, não na página: assim trocar de aba é navegação de verdade
 * (URL própria, botão voltar funciona, dá para linkar direto) e o dropdown da sidebar
 * aponta para cada uma.
 */
export default function ConfiguracoesLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)

  if (!hidratado) {
    return <div className={styles.skeleton} aria-label="Carregando configurações" />
  }

  // Configurações da igreja mexem em dado institucional e expõem financeiro entre igrejas.
  if (role !== 'ADMIN_IGREJA') {
    return <AcessoRestrito />
  }

  const abaAtual = ABAS.find((a) => pathname.startsWith(a.href))

  return (
    <div className={styles.pagina}>
      <header>
        <div className={styles.breadcrumb}>
          <span>Sistema</span>
          <ChevronRight size={12} aria-hidden="true" />
          <span className={styles.breadcrumbAtivo}>Configurações</span>
        </div>
        <h1 className={styles.titulo}>{abaAtual?.label ?? 'Configurações'}</h1>
      </header>

      {/* São links de navegação (mudam a URL), não um widget de abas: usar role="tab"
          prometeria ao leitor de tela um tabpanel que não existe. `aria-current` é o certo. */}
      <nav className={styles.abas}>
        {ABAS.map((aba) => {
          const ativa = pathname.startsWith(aba.href)
          return (
            <Link
              key={aba.href}
              href={aba.href}
              aria-current={ativa ? 'page' : undefined}
              className={`${styles.aba} ${ativa ? styles.abaAtiva : ''}`}
            >
              {aba.label}
            </Link>
          )
        })}
      </nav>

      {children}
    </div>
  )
}
