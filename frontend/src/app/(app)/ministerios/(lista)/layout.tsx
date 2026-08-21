'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import styles from './layout.module.css'

const ABAS = [
  { href: '/ministerios', label: 'Ativas' },
  { href: '/ministerios/arquivados', label: 'Arquivadas' },
]

export default function MinisteriosLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const { ministerio } = useRotulos()

  return (
    <div className={styles.moduloWrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{ministerio.plural}</span>
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
