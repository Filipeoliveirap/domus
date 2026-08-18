'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import styles from './categoriasLayout.module.css'

const ABAS = [
  { href: '/financeiro/categorias', label: 'Ativas' },
  { href: '/financeiro/categorias/arquivadas', label: 'Arquivadas' },
]

export default function CategoriasLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className={styles.wrapper}>
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

      {children}
    </div>
  )
}
