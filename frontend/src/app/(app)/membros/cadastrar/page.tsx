'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useMembroForm } from '@/hooks/membro/useCadastrarMembro'
import { MembroForm } from '@/components/module/membros/MembroForm'
import styles from './page.module.css'  

export default function CadastrarMembroPage() {
  const form = useMembroForm() 

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/membros" className={styles.breadcrumbLink}>Membros</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Novo membro</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Cadastrar membro</h1>
      </header>

      <MembroForm {...form} />
    </div>
  )
}