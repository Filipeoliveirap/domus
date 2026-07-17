'use client'

import { useParams, useRouter } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useMovimentacao } from '@/hooks/financeiro/movimentacao/useMovimentacao'
import { useMovimentacaoForm } from '@/hooks/financeiro/movimentacao/useMovimentacaoForm'
import { MovimentacaoForm } from '@/components/module/movimentacoes/MovimentacaoForm'
import styles from '../cadastrar/cadastrar.module.css'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { SkeletonMovimentacaoForm } from './SkeletonMovimentacaoForm'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

export default function EditarMovimentacaoPage() {
  const params = useParams()
  const router = useRouter()
  const id = params.id as string
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = role === 'ADMIN_IGREJA'

  const { data: movimentacao, isPending, isError, refetch } = useMovimentacao(id)
  const form = useMovimentacaoForm({
    movimentacaoId: id,
    movimentacaoInicial: movimentacao,
    onSuccess: () => router.push('/financeiro/movimentacoes'),
  })

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <SkeletonMovimentacaoForm />
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (isPending) {
    return (
      <div className={styles.pagina}>
        <SkeletonMovimentacaoForm />
      </div>
    )
  }

  if (isError || !movimentacao) {
    return (
      <div className={styles.pagina}>
        <EstadoErro
          titulo="Movimentação não encontrada"
          mensagem="Não foi possível carregar esta movimentação. Verifique sua conexão e tente novamente."
          aoTentarNovamente={() => refetch()}
        />
      </div>
    )
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