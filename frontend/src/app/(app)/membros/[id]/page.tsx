'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useMembro } from '@/hooks/membro/useMembro'
import { useMembroForm } from '@/hooks/membro/useMembroForm'
import { MembroForm } from '@/components/module/membros/MembroForm'
import styles from '../cadastrar/page.module.css'  

export default function EditarMembroPage() {
  const params = useParams()
  const id = params.id as string
  console.log('ID da rota:', id, 'params completo:', params)  

  const { data: membro, isLoading: carregando, isError } = useMembro(id)
  const form = useMembroForm({ membroId: id, membroInicial: membro })

  if (carregando) {
    return <div className={styles.pagina}><p>Carregando membro…</p></div>
  }
  if (isError || !membro) {
    return <div className={styles.pagina}><p>Membro não encontrado.</p></div>
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