'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useEventoForm } from '@/hooks/evento/useEventoForm'
import { EventoForm } from '@/components/module/eventos/EventoForm'
import styles from '@/components/module/eventos/EventoForm.module.css'

export default function EditarEventoPage() {
  const params = useParams()
  const id = params.id as string

  const { data: evento, isPending, isError } = useEvento(id)
  const form = useEventoForm({ eventoId: id, eventoInicial: evento })

  if (isPending) {
    return <div className={styles.pagina}><p>Carregando evento…</p></div>
  }
  if (isError || !evento) {
    return <div className={styles.pagina}><p>Evento não encontrado.</p></div>
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>{evento.titulo}</span>
      </nav>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Editar evento</h1>
      </header>

      <EventoForm {...form} />
    </div>
  )
}