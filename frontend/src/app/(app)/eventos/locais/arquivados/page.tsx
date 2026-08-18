'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, MapPinned } from 'lucide-react'
import { useLocaisEventoArquivados } from '@/hooks/evento/useLocaisEventoArquivados'
import { useRestaurarLocalEvento } from '@/hooks/evento/useRestaurarLocalEvento'
import { useExcluirLocalEventoDefinitivamente } from '@/hooks/evento/useExcluirLocalEventoDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalDetalheLocal } from '@/components/module/eventos/ModalDetalheLocal'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import type { LocalEventoResponse } from '@/types/evento.type'
import styles from './arquivados.module.css'

export default function LocaisEventoArquivadosPage() {
  const { data: locais, isLoading, isError, refetch } = useLocaisEventoArquivados()
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarEventos(role)
  const { restaurar, isLoading: restaurando } = useRestaurarLocalEvento()
  const [excluindo, setExcluindo] = useState<LocalEventoResponse | null>(null)
  const [detalhe, setDetalhe] = useState<LocalEventoResponse | null>(null)

  if (!podeGerenciar) {
    return <AcessoRestrito />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="64px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!locais || locais.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhum local arquivado" mensagem="Locais arquivados aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {locais.map((local) => (
          <div key={local.id} className={styles.linha} onClick={() => setDetalhe(local)}>
            <div className={styles.info}>
              <div className={styles.icone}><MapPinned size={18} /></div>
              <div>
                <p className={styles.nome}>{local.nome}</p>
                <p className={styles.detalhe}>{local.capacidade ? `Capacidade: ${local.capacidade}` : 'Sem capacidade definida'}</p>
              </div>
            </div>
            <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(local.id, local.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button className={styles.botaoExcluir} onClick={() => setExcluindo(local)}>
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo local={excluindo} onClose={() => setExcluindo(null)} />
      )}

      {detalhe && (
        <ModalDetalheLocal local={detalhe} onClose={() => setDetalhe(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ local, onClose }: { local: LocalEventoResponse; onClose: () => void }) {
  const { confirmar, isLoading } = useExcluirLocalEventoDefinitivamente(local, onClose)

  return (
    <ModalConfirmacao
      titulo="Excluir local definitivamente?"
      mensagem={<>Isso vai apagar <strong>{local.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
