'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useMembro } from '@/hooks/membro/useMembro'
import { useMembroForm } from '@/hooks/membro/useMembroForm'
import { MembroForm } from '@/components/module/membros/MembroForm'
import styles from '../cadastrar/page.module.css'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { SkeletonMembroForm } from './SkeletonMembroForm'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

export default function EditarMembroPage() {
  const params = useParams()
  const id = params.id as string
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = role === 'ADMIN_IGREJA'

  const { data: membro, isLoading: carregando, isError, refetch } = useMembro(id)
  const form = useMembroForm({ membroId: id, membroInicial: membro })

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <SkeletonMembroForm />
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (carregando) {
    return (
      <div className={styles.pagina}>
        <SkeletonMembroForm />
      </div>
    )
  }

  if (isError || !membro) {
    return (
      <div className={styles.pagina}>
        <EstadoErro
          titulo="Membro não encontrado"
          mensagem="Não foi possível carregar este membro. Verifique sua conexão e tente novamente."
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
        <Link href="/membros" className={styles.breadcrumbLink}>Membros</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{membro.nome}</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Editar membro</h1>
      </header>

      <MembroForm {...form} />
    </div>
  )
}