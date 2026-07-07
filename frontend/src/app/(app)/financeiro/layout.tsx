'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import styles from './financeiro.module.css'

const ABAS = [
  { href: '/financeiro/movimentacoes', label: 'Movimentações' },
  { href: '/financeiro/categorias', label: 'Categorias' },
  { href: '/financeiro/relatorios', label: 'Relatórios' },
]

export default function FinanceiroLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className={styles.moduloWrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Financeiro</span>
      </nav>

      <div className={styles.abas}>
        {ABAS.map((aba) => {
          const ativa = pathname.startsWith(aba.href)
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