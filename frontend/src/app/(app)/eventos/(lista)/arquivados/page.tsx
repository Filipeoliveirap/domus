'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, CalendarDays } from 'lucide-react'
import { useEventosArquivados } from '@/hooks/evento/useEventosArquivados'
import { useRestaurarEvento } from '@/hooks/evento/useRestaurarEvento'
import { useExcluirEventoDefinitivamente } from '@/hooks/evento/useExcluirEventoDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalEscopoEdicaoEvento } from '@/components/module/eventos/ModalEscopoEdicaoEvento'
import { DrawerDetalheEvento } from '@/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import { dataExtenso, hora } from '@/lib/formats/eventoFormat'
import type { EventoArquivadoResponse } from '@/types/evento.type'
import styles from './arquivados.module.css'

export default function EventosArquivadosPage() {
  const { data: eventos, isLoading, isError, refetch } = useEventosArquivados()
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarEventos(role)
  const { restaurar, isLoading: restaurando } = useRestaurarEvento()
  const [excluindo, setExcluindo] = useState<EventoArquivadoResponse | null>(null)
  const [detalheId, setDetalheId] = useState<string | null>(null)
  // Evento de série pergunta o alcance (só este/estes e os seguintes/toda a série) antes de
  // restaurar — evento avulso (serieId null) restaura direto, sem esse passo a mais.
  const [restaurandoComEscopo, setRestaurandoComEscopo] = useState<EventoArquivadoResponse | null>(null)

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem="Só administradores e líderes veem eventos arquivados." />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="72px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!eventos || eventos.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhum evento arquivado" mensagem="Eventos arquivados aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {eventos.map((e) => (
          <div key={e.id} className={styles.linha} onClick={() => setDetalheId(e.id)}>
            <div className={styles.info}>
              <div className={styles.icone}><CalendarDays size={18} /></div>
              <div>
                <p className={styles.titulo}>{e.titulo}</p>
                <p className={styles.detalhe}>
                  {dataExtenso(e.inicioEm)} · {hora(e.inicioEm)}
                  {e.tipo ? ` · ${e.tipo}` : ''}
                </p>
              </div>
            </div>
            <div className={styles.acoes} onClick={(evt) => evt.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => (e.serieId ? setRestaurandoComEscopo(e) : restaurar(e.id, e.titulo))}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button className={styles.botaoExcluir} onClick={() => setExcluindo(e)}>
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo evento={excluindo} onClose={() => setExcluindo(null)} />
      )}

      {restaurandoComEscopo && (
        <ModalEscopoEdicaoEvento
          titulo={restaurandoComEscopo.titulo}
          pergunta="O que você quer restaurar?"
          onEscolher={(escopo) => {
            restaurar(restaurandoComEscopo.id, restaurandoComEscopo.titulo, escopo)
            setRestaurandoComEscopo(null)
          }}
          onClose={() => setRestaurandoComEscopo(null)}
        />
      )}

      {detalheId && (
        <DrawerDetalheEvento eventoId={detalheId} onClose={() => setDetalheId(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ evento, onClose }: { evento: EventoArquivadoResponse; onClose: () => void }) {
  const { confirmar, isLoading } = useExcluirEventoDefinitivamente(evento, onClose)

  // O que se perde já foi avisado (e confirmado por escrito) no arquivar — aqui é só o
  // último passo, confirmação simples, igual local de evento e movimentação sem contribuinte.
  return (
    <ModalConfirmacao
      titulo="Excluir evento definitivamente?"
      mensagem={
        <>
          Isso vai apagar <strong>{evento.titulo}</strong> de vez
          {evento.temVinculo ? <>, junto com {evento.totalInscritos === 1 ? 'a inscrição' : `as ${evento.totalInscritos} inscrições`}</> : null}.
          Não tem como desfazer.
        </>
      }
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
