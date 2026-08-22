'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useEventoForm } from '@/hooks/evento/useEventoForm'
import { EventoForm } from '@/components/module/eventos/EventoForm'
import styles from '@/components/module/eventos/EventoForm.module.css'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { SkeletonEventoForm } from './SkeletonEventoForm'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

export default function EditarEventoPage() {
  const params = useParams()
  const id = params.id as string
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeGerenciarEventos(role)

  const { data: evento, isPending, isError, refetch } = useEvento(id)
  const form = useEventoForm({ eventoId: id, eventoInicial: evento })

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <SkeletonEventoForm />
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (isPending) {
    return (
      <div className={styles.pagina}>
        <SkeletonEventoForm />
      </div>
    )
  }

  if (isError || !evento) {
    return (
      <div className={styles.pagina}>
        <EstadoErro
          titulo="Evento não encontrado"
          mensagem="Não foi possível carregar este evento. Verifique sua conexão e tente novamente."
          aoTentarNovamente={() => refetch()}
        />
      </div>
    )
  }

  if (!evento.podeGerenciarEsteEvento) {
    return <AcessoRestrito />
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

      <EventoForm {...form} eventoId={id} />
    </div>
  )
}