'use client'

import { useState } from 'react'
import { X, UserPlus, Plus, ArrowLeftRight, Users, UserRound, Search } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useVisitantes } from '@/hooks/visitante/useVisitantes'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { iniciaisVisitante } from '@/lib/formats/visitanteFormat'
import { formatarTelefoneExibicao } from '@/lib/formats/visitanteFormat'
import { iniciais as iniciaisPessoa } from '@/lib/formats/pessoaFormat'
import styles from './ModalAdicionarMembro.module.css'

interface ModalAdicionarMembroProps {
  celulaId: string
  membrosPessoaIds: Set<string>
  membrosVisitanteIds: Set<string>
  onClose: () => void
  onCadastrarExterno: () => void
}

type Tab = 'pessoas' | 'visitantes'

export function ModalAdicionarMembro({ celulaId, membrosPessoaIds, membrosVisitanteIds, onClose, onCadastrarExterno }: ModalAdicionarMembroProps) {
  const [tab, setTab] = useState<Tab>('pessoas')
  const [busca, setBusca] = useState('')
  const queryClient = useQueryClient()

  const { data: pessoasData } = usePessoas({ q: tab === 'pessoas' ? busca : '', page: 0, size: 50 })
  const { data: visitantesData } = useVisitantes({
    q: tab === 'visitantes' ? busca : '', page: 0, size: 50,
  })

  // Já vinculado à célula some da lista — evita adicionar de novo e dá o feedback
  // visual de que funcionou, sem fechar o modal (dá pra adicionar vários seguidos).
  const pessoas = (pessoasData?.content ?? []).filter(p => !membrosPessoaIds.has(p.id))
  const visitantes = (visitantesData?.content ?? []).filter(v => !membrosVisitanteIds.has(v.id))

  async function handleAdicionarPessoa(pessoaId: string) {
    try {
      await celulaService.adicionarMembro(celulaId, { pessoaId })
      invalidarCache(queryClient, 'celula')
      notificar.sucesso('Pessoa adicionada à célula.')
    } catch { notificar.erro('Erro ao adicionar.') }
  }

  async function handleAdicionarVisitante(visitanteId: string) {
    try {
      await celulaService.adicionarMembro(celulaId, { visitanteId })
      invalidarCache(queryClient, 'celula')
      queryClient.invalidateQueries({ queryKey: ['visitantes'] })
      notificar.sucesso('Visitante adicionado à célula.')
    } catch { notificar.erro('Erro ao adicionar.') }
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={e => e.stopPropagation()}>
        <button className={styles.close} onClick={onClose}><X size={18} /></button>
        <h2 className={styles.titulo}>Adicionar à Célula</h2>

        <div className={styles.tabs}>
          <button className={`${styles.tab} ${tab === 'pessoas' ? styles.tabAtiva : ''}`}
            onClick={() => setTab('pessoas')}>
            <Users size={16} /> Pessoas
          </button>
          <button className={`${styles.tab} ${tab === 'visitantes' ? styles.tabAtiva : ''}`}
            onClick={() => setTab('visitantes')}>
            <UserRound size={16} /> Visitantes
          </button>
        </div>

        <div className={styles.buscaWrap}>
          <Search size={16} className={styles.buscaIcon} />
          <input className={styles.buscaInput} placeholder="Buscar por nome..."
            value={busca} onChange={e => setBusca(e.target.value)} />
        </div>

        <div className={styles.lista}>
          {tab === 'pessoas' ? (
            pessoas.map(p => (
              <div key={p.id} className={styles.item} role="button" tabIndex={0}
                onClick={() => handleAdicionarPessoa(p.id)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleAdicionarPessoa(p.id) }}
                title="Adicionar à célula">
                <div className={styles.itemInfo}>
                  <span className={styles.avatar}>{iniciaisPessoa(p.nome)}</span>
                  <div>
                    <p className={styles.itemNome}>{p.nome}</p>
                    <p className={styles.itemSub}>{formatarTelefoneExibicao(p.telefone)}</p>
                  </div>
                </div>
                <span className={styles.btnAdd}>
                  <Plus size={18} />
                </span>
              </div>
            ))
          ) : (
            visitantes.map(v => (
              <div key={v.id} className={styles.item} role="button" tabIndex={0}
                onClick={() => handleAdicionarVisitante(v.id)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleAdicionarVisitante(v.id) }}
                title="Adicionar à célula">
                <div className={styles.itemInfo}>
                  <span className={styles.avatar}>{iniciaisVisitante(v.nome)}</span>
                  <div>
                    <p className={styles.itemNome}>{v.nome}</p>
                    <p className={styles.itemSub}>{formatarTelefoneExibicao(v.telefone)}</p>
                  </div>
                </div>
                <span className={styles.btnAdd}>
                  <Plus size={18} />
                </span>
              </div>
            ))
          )}
        </div>

        <button className={styles.btnExterno} onClick={onCadastrarExterno}>
          <UserPlus size={16} /> Cadastrar visitante externo
        </button>
      </div>
    </div>
  )
}
