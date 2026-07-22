'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useCadastrarPessoa } from '@/hooks/pessoa/useCadastrarPessoa'
import { PessoaForm } from '@/components/module/pessoas/PessoaForm'
import styles from './page.module.css'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { podeGerenciarPessoas } from '@/lib/permissoes'

export default function CadastrarPessoaPage() {
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeGerenciarPessoas(role)

  const form = useCadastrarPessoa()

  if (!hidratado) {
    return <div className={styles.pagina} />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/pessoas" className={styles.breadcrumbLink}>Pessoas</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Nova pessoa</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Cadastrar pessoa</h1>
      </header>

      <PessoaForm {...form} />
    </div>
  )
}
