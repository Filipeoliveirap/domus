'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { usePessoa } from '@/hooks/pessoa/usePessoa'
import { usePessoaForm } from '@/hooks/pessoa/usePessoaForm'
import { PessoaForm } from '@/components/module/pessoas/PessoaForm'
import styles from '../cadastrar/page.module.css'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { SkeletonPessoaForm } from './SkeletonPessoaForm'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { podeGerenciarPessoas } from '@/lib/permissoes'

export default function EditarPessoaPage() {
  const params = useParams()
  const id = params.id as string
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const autorizado = podeGerenciarPessoas(role, capacidadesExtras)

  const { data: pessoa, isLoading: carregando, isError, refetch } = usePessoa(id)
  const form = usePessoaForm({ pessoaId: id, pessoaInicial: pessoa })

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <SkeletonPessoaForm />
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (carregando) {
    return (
      <div className={styles.pagina}>
        <SkeletonPessoaForm />
      </div>
    )
  }

  if (isError || !pessoa) {
    return (
      <div className={styles.pagina}>
        <EstadoErro
          titulo="Pessoa não encontrada"
          mensagem="Não foi possível carregar esta pessoa. Verifique sua conexão e tente novamente."
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
        <Link href="/pessoas" className={styles.breadcrumbLink}>Pessoas</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{pessoa.nome}</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Editar pessoa</h1>
      </header>

      <PessoaForm {...form} />
    </div>
  )
}
