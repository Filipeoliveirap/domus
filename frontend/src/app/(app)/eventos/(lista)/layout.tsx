'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import styles from './layout.module.css'

export default function EventosListaLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarEventos(role)

  return (
    <div className={styles.wrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Eventos</span>
      </nav>

      {podeGerenciar && (
        <div className={styles.abas}>
          <Link href="/eventos" className={`${styles.aba} ${pathname === '/eventos' ? styles.abaAtiva : ''}`}>
            Ativos
          </Link>
          <Link
            href="/eventos/arquivados"
            className={`${styles.aba} ${pathname === '/eventos/arquivados' ? styles.abaAtiva : ''}`}
          >
            Arquivados
          </Link>
        </div>
      )}

      {children}
    </div>
  )
}
