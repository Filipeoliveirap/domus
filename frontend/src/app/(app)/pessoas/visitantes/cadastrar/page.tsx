'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useVisitanteForm } from '@/hooks/visitante/useVisitanteForm'
import { VisitanteForm } from '@/components/module/visitantes/VisitanteForm'
import styles from './page.module.css'

export default function CadastrarVisitantePage() {
  const form = useVisitanteForm()

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/pessoas/visitantes" className={styles.breadcrumbLink}>Visitantes</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Cadastrar</span>
      </nav>

      <h1 className={styles.titulo}>Novo Visitante</h1>
      <p className={styles.subtitulo}>
        Registre as informações de um novo visitante para manter o acompanhamento pastoral.
      </p>

      <VisitanteForm {...form} />
    </div>
  )
}
