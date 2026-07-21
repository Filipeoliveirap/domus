'use client'

import { useEffect } from 'react'
import Link from 'next/link'
import { X, Clock, MapPin, CalendarDays, Users } from 'lucide-react'
import { useEvento } from '@/hooks/evento/useEvento'
import { useAuthStore } from '@/store/authStore'
import {
  statusEvento,
  rotuloStatus,
  varianteStatus,
  dataExtenso,
  hora,
} from '@/lib/formats/eventoFormat'
import styles from './DrawerDetalheEvento.module.css'
import { SkeletonDrawerEvento } from "./SkeletonDrawerEvento";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

interface DrawerDetalheEventoProps {
  eventoId: string
  onClose: () => void
}

export function DrawerDetalheEvento({ eventoId, onClose }: DrawerDetalheEventoProps) {
  const { data: evento, isPending, isError, refetch } = useEvento(eventoId)
  const role = useAuthStore((s) => s.role)
  const podeVerInscritos = role === 'ADMIN_IGREJA' || role === 'LIDER'

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const status = evento ? statusEvento(evento) : null

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <aside
        className={styles.drawer}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        {isPending ? (
          <SkeletonDrawerEvento />
        ) : isError || !evento ? (
          <EstadoErro
            titulo="Não foi possível carregar o evento"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : (
          <div className={styles.conteudo}>
            {/* Cabeçalho */}
            <header className={styles.header}>
              {status && (
                <span className={`${styles.selo} ${styles[varianteStatus(status)]}`}>
                  {rotuloStatus(status)}
                </span>
              )}
              <span className={styles.dataTopo}>{dataExtenso(evento.inicioEm)}</span>
              <h2 className={styles.titulo}>{evento.titulo}</h2>
            </header>

            {/* Infos */}
            <div className={styles.infos}>
              <div className={styles.infoItem}>
                <span className={styles.infoIcone}><Clock size={20} /></span>
                <div>
                  <p className={styles.infoLabel}>Horário</p>
                  <p className={styles.infoValor}>
                    {hora(evento.inicioEm)}
                    {evento.fimEm && ` — ${hora(evento.fimEm)}`}
                  </p>
                </div>
              </div>

              {evento.local && (
                <div className={styles.infoItem}>
                  <span className={styles.infoIcone}><MapPin size={20} /></span>
                  <div>
                    <p className={styles.infoLabel}>Local</p>
                    <p className={styles.infoValor}>{evento.local}</p>
                  </div>
                </div>
              )}
            </div>

            {/* Descrição */}
            {evento.descricao && (
              <div className={styles.descricaoBloco}>
                <p className={styles.infoLabel}>Descrição</p>
                <p className={styles.descricaoTexto}>{evento.descricao}</p>
              </div>
            )}

            {/* Imagem */}
            <div className={styles.imagemBloco}>
              {evento.foto ? (
                <img src={evento.foto} alt={evento.titulo} className={styles.imagem} />
              ) : (
                <div className={styles.imagemPlaceholder}>
                  <CalendarDays size={40} />
                </div>
              )}
            </div>

            {podeVerInscritos && evento.requerInscricao && (
              <Link href={`/eventos/${evento.id}/inscritos`} className={styles.acaoInscritos}>
                <Users size={18} />
                Ver inscritos
              </Link>
            )}
          </div>
        )}
      </aside>
    </div>
  )
}