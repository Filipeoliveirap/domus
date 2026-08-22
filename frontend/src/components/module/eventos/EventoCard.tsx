'use client'

import { useRouter } from 'next/navigation'
import { CalendarDays, MapPin, Pencil, Archive, Building2 } from 'lucide-react'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { useAuthStore } from '@/store/authStore'
import {
  dataAgenda,
  seloEvento,
  podeEditarEvento,
  podeArquivarEvento,
} from '@/lib/formats/eventoFormat'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { EventoResponse } from '@/types/evento.type'
import { urlFoto } from '@/lib/urlFoto'
import { SelosInscricaoCard } from './SelosInscricaoCard'
import styles from './EventoCard.module.css'

interface EventoCardProps {
  evento: EventoResponse
  onAbrirDetalhe: (evento: EventoResponse) => void
  onArquivar: (evento: EventoResponse) => void
  /** Clique no selo de pendência: abre o mesmo detalhe, mas já com o modal de resposta
   *  aberto — se não vier, o selo cai pro comportamento padrão de abrir o detalhe. */
  onAbrirPendencia?: (evento: EventoResponse) => void
}

export function EventoCard({ evento, onAbrirDetalhe, onArquivar, onAbrirPendencia }: EventoCardProps) {
  const router = useRouter()
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)

  const podeGerenciar = evento.podeGerenciarEsteEvento
  const ehOutraIgreja = evento.igrejaOrganizadora.id !== minhaIgrejaId
  const { dia, mes, ano } = dataAgenda(evento.inicioEm)
  const { label: seloLabel, variante: seloVariante } = seloEvento(evento)

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
      className={`${styles.card} ${evento.situacao === 'ENCERRADO' ? styles.cardEncerrado : ''}`}
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
        <div className={styles.selos}>
          <span className={`${styles.selo} ${styles[seloVariante]}`}>
            <span className={styles.seloDot} />
            {seloLabel}
          </span>
          {/* Só aparece quando o evento tem um recorte NOMEADO (Kids, Jovens…) — faixa
              etária digitada à mão ou sem restrição nenhuma não geram selo (comportamento
              correto, não é bug: o nome é o que dá sentido ao selo). */}
          {evento.recorteEtario && (
            <span className={`${styles.selo} ${styles.seloRecorte}`}>
              {evento.recorteEtario}
            </span>
          )}
          {ehOutraIgreja && (
            <span className={`${styles.selo} ${styles.seloIgreja}`}>
              <Building2 size={12} aria-hidden="true" />
              {evento.igrejaOrganizadora.sigla ?? evento.igrejaOrganizadora.nome}
            </span>
          )}
        </div>

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
          <SelosInscricaoCard
            evento={evento}
            onAbrirPendencia={() => (onAbrirPendencia ? onAbrirPendencia(evento) : onAbrirDetalhe(evento))}
          />
        </div>
      </div>
    </article>
  )
}