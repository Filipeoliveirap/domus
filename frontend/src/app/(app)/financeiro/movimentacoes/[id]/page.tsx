'use client'

import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useMovimentacao } from '@/hooks/financeiro/movimentacao/useMovimentacao'
import { useMovimentacaoForm } from '@/hooks/financeiro/movimentacao/useMovimentacaoForm'
import { MovimentacaoForm } from '@/components/module/movimentacoes/MovimentacaoForm'
import styles from '../cadastrar/cadastrar.module.css'

export default function EditarMovimentacaoPage() {
  const params = useParams()
  const router = useRouter()
  const id = params.id as string

  const { data: movimentacao, isPending, isError } = useMovimentacao(id)
  const form = useMovimentacaoForm({
    movimentacaoId: id,
    movimentacaoInicial: movimentacao,
    onSuccess: () => router.push('/financeiro/movimentacoes'),
  })

  if (isPending) {
    return <div className={styles.pagina}><p>Carregando movimentação…</p></div>
  }
  if (isError || !movimentacao) {
    return <div className={styles.pagina}><p>Movimentação não encontrada.</p></div>
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/financeiro/movimentacoes" className={styles.breadcrumbLink}>Movimentações</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Editar</span>
      </nav>

      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Editar movimentação</h1>
      </header>
      <MovimentacaoForm {...form} membroNomeInicial={movimentacao.membroNome ?? undefined} />
    </div>
  )
}