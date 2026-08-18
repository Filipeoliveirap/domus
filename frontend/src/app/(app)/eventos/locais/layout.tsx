'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import styles from './layout.module.css'

const ABAS = [
  { href: '/eventos/locais', label: 'Ativos' },
  { href: '/eventos/locais/arquivados', label: 'Arquivados' },
]

export default function LocaisEventoLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className={styles.moduloWrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Locais</span>
      </nav>

      <div className={styles.abas}>
        {ABAS.map((aba) => {
          const ativa = pathname === aba.href
          return (
            <Link
              key={aba.href}
              href={aba.href}
              className={`${styles.aba} ${ativa ? styles.abaAtiva : ''}`}
            >
              {aba.label}
            </Link>
          )
        })}
      </div>

      <div className={styles.conteudo}>{children}</div>
    </div>
  )
}
