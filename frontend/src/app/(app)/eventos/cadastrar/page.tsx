'use client'

import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useEventoForm } from '@/hooks/evento/useEventoForm'
import { EventoForm } from '@/components/module/eventos/EventoForm'
import styles from '@/components/module/eventos/EventoForm.module.css'

export default function CadastrarEventoPage() {
  const form = useEventoForm()

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Novo evento</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Cadastrar evento</h1>
      </header>

      <EventoForm {...form} />
    </div>
  )
}