'use client'

import { useRouter } from 'next/navigation'
import { CalendarDays, MapPin, Pencil, Archive } from 'lucide-react'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { useAuthStore } from '@/store/authStore'
import {
  dataAgenda,
  statusEvento,
  rotuloStatus,
  varianteStatus,
  rotuloSituacao,
  varianteSituacao,
  podeEditarEvento,
  podeArquivarEvento,
} from '@/lib/formats/eventoFormat'
import { podeGerenciarEventos } from '@/lib/permissoes'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { EventoResponse } from '@/types/evento.type'
import { urlFoto } from '@/lib/urlFoto'
import styles from './EventoCard.module.css'

interface EventoCardProps {
  evento: EventoResponse
  onAbrirDetalhe: (evento: EventoResponse) => void
  onArquivar: (evento: EventoResponse) => void
}

export function EventoCard({ evento, onAbrirDetalhe, onArquivar }: EventoCardProps) {
  const router = useRouter()
  const role = useAuthStore((s) => s.role)

  const podeGerenciar = podeGerenciarEventos(role)
  const status = statusEvento(evento)
  const { dia, mes, ano } = dataAgenda(evento.inicioEm)

  // Fora de AGENDADO, o selo passa a mostrar a situação real do backend ("Em andamento" /
  // "Encerrado") em vez do rótulo cosmético de data (EM_BREVE/HOJE) — é o que decide se a
  // edição está travada, então é o que a pessoa precisa ver primeiro.
  const seloLabel = evento.situacao === 'AGENDADO' ? rotuloStatus(status) : rotuloSituacao(evento.situacao)
  const seloVariante = evento.situacao === 'AGENDADO' ? varianteStatus(status) : varianteSituacao(evento.situacao)

  // A situação real (backend) trava edição/arquivamento — o servidor recusa do mesmo jeito,
  // então a ação nem aparece no menu pra não gerar um erro evitável.
  const acoes: ItemAcao[] = []
  if (podeEditarEvento(evento.situacao)) {
    acoes.push({ label: 'Editar', icone: Pencil, onClick: () => router.push(`/eventos/${evento.id}`) })
  }
  if (podeArquivarEvento(evento.situacao)) {
    acoes.push({ label: 'Arquivar', icone: Archive, onClick: () => onArquivar(evento), perigo: true, separadorAntes: true })
  }

  return (
    <article
      className={`${styles.card} ${status === 'ENCERRADO' ? styles.cardEncerrado : ''}`}
      onClick={() => onAbrirDetalhe(evento)}
    >
      <div className={styles.imagem}>
        {/*
          DISPLAY, não THUMB: o `thumb` tem 200px e foi dimensionado para AVATAR (40px na
          tela, folga de sobra). Este banner ocupa a largura inteira do card — uns 350px —
          e pedir a versão de 200px aqui obriga o navegador a ampliar, o que borra.
        */}
        {urlFoto(evento.fotoId, 'DISPLAY') ? (
          <img src={urlFoto(evento.fotoId, 'DISPLAY')!} alt={evento.titulo} className={styles.imagemFoto} />
        ) : (
          <div className={styles.imagemPlaceholder}>
            <CalendarDays size={32} />
          </div>
        )}
        <span className={`${styles.selo} ${styles[seloVariante]}`}>
          <span className={styles.seloDot} />
          {seloLabel}
        </span>

        {podeGerenciar && acoes.length > 0 && (
          <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
            <MenuAcoes itens={acoes} />
          </div>
        )}
      </div>

      {/* Corpo */}
      <div className={styles.corpo}>
        <div className={styles.dataBox}>
          <span className={styles.dataMes}>{mes}</span>
          <span className={styles.dataDia}>{dia}</span>
          {ano && <span className={styles.dataAno}>{ano}</span>}
        </div>

        <div className={styles.info}>
          <h3 className={styles.titulo}>{evento.titulo}</h3>
          {evento.descricao && (
            <p className={styles.descricao}>{evento.descricao}</p>
          )}
          {evento.local && (
            <div className={styles.local}>
              <MapPin size={14} />
              <span>{evento.local.nome}</span>
            </div>
          )}
          {evento.preco != null && (
            <span className={styles.preco}>{formatarMoeda(evento.preco)}</span>
          )}
        </div>
      </div>
    </article>
  )
}