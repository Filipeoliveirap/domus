'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useMovimentacaoForm } from '@/hooks/financeiro/movimentacao/useMovimentacaoForm'
import { MovimentacaoForm } from '@/components/module/movimentacoes/MovimentacaoForm'
import styles from './cadastrar.module.css'


export default function CadastrarMovimentacaoPage() {
  const form = useMovimentacaoForm({
    onSuccess: () => {
    },
  })

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/financeiro/movimentacoes" className={styles.breadcrumbLink}>Movimentações</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Nova</span>
      </nav>

      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Nova movimentação</h1>
      </header>

      <MovimentacaoForm {...form} />
    </div>
  )
}