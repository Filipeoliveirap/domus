'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarPessoas } from '@/lib/permissoes'
import styles from './layout.module.css'

export default function PessoasListaLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const podeGerenciar = podeGerenciarPessoas(role, capacidadesExtras)

  return (
    <div className={styles.wrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Pessoas</span>
      </nav>

      {podeGerenciar && (
        <div className={styles.abas}>
          <Link href="/pessoas" className={`${styles.aba} ${pathname === '/pessoas' ? styles.abaAtiva : ''}`}>
            Ativas
          </Link>
          <Link
            href="/pessoas/arquivados"
            className={`${styles.aba} ${pathname === '/pessoas/arquivados' ? styles.abaAtiva : ''}`}
          >
            Arquivadas
          </Link>
        </div>
      )}

      {children}
    </div>
  )
}
