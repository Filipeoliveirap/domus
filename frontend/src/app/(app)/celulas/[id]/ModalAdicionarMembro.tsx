'use client'

import { useEffect, useState } from 'react'
import { X, UserPlus, Users, UserRound, Search } from 'lucide-react'
import { clsx } from 'clsx'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useVisitantes } from '@/hooks/visitante/useVisitantes'
import { useDebounce } from '@/hooks/useDebounce'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { Input } from '@/components/common/input/Input'
import { urlFoto } from '@/lib/urlFoto'
import { iniciaisVisitante } from '@/lib/formats/visitanteFormat'
import { iniciais as iniciaisPessoa } from '@/lib/formats/pessoaFormat'
import { useRotulos } from '@/lib/rotulos/useRotulos'
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
  const buscaDebounced = useDebounce(busca, 300)
  const queryClient = useQueryClient()
  const { celula } = useRotulos()
  const { saindo, fechar } = useFecharAnimado(onClose, 260)
  // ids sendo adicionados — a linha colapsa animada antes do refetch tirá-la da lista.
  const [adicionando, setAdicionando] = useState<Set<string>>(() => new Set())
  // ids já adicionados nesta sessão do modal — filtra a lista pra sempre (não espera o
  // refetch). Sem isso, se o refetch demora mais que o timer, a linha "pisca de volta".
  const [jaAdicionados, setJaAdicionados] = useState<Set<string>>(() => new Set())
  const semId = (s: Set<string>, id: string) => {
    const n = new Set(s)
    n.delete(id)
    return n
  }

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape') fechar() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  const { data: pessoasData } = usePessoas({ q: tab === 'pessoas' ? buscaDebounced : '', page: 0, size: 50 })
  const { data: visitantesData } = useVisitantes({
    q: tab === 'visitantes' ? buscaDebounced : '', page: 0, size: 50,
  })

  const pessoas = (pessoasData?.content ?? []).filter(p => !membrosPessoaIds.has(p.id) && !jaAdicionados.has(p.id))
  const visitantes = (visitantesData?.content ?? []).filter(v => !membrosVisitanteIds.has(v.id) && !jaAdicionados.has(v.id))

  function adicionarComAnimacao(id: string, chamada: () => Promise<unknown>, sucesso: string) {
    if (adicionando.has(id)) return
    setAdicionando(s => new Set(s).add(id))
    setTimeout(async () => {
      try {
        await chamada()
        invalidarCache(queryClient, 'celula')
        notificar.sucesso(sucesso)
        setJaAdicionados(s => new Set(s).add(id))
        setTimeout(() => setAdicionando(s => semId(s, id)), 350)
      } catch {
        notificar.erro('Erro ao adicionar.')
        setAdicionando(s => semId(s, id))
      }
    }, 380)
  }

  function adicionarPessoa(pessoaId: string) {
    adicionarComAnimacao(
      pessoaId,
      () => celulaService.adicionarMembro(celulaId, { pessoaId }),
      `Pessoa adicionada à ${celula.singular.toLowerCase()}.`,
    )
  }

  function adicionarVisitante(visitanteId: string) {
    adicionarComAnimacao(
      visitanteId,
      async () => {
        await celulaService.adicionarMembro(celulaId, { visitanteId })
        queryClient.invalidateQueries({ queryKey: ['visitantes'] })
      },
      `Visitante adicionado à ${celula.singular.toLowerCase()}.`,
    )
  }

  const lista = tab === 'pessoas'
    ? pessoas.map(p => ({ id: p.id, nome: p.nome, foto: urlFoto(p.fotoId, 'THUMB'), iniciais: iniciaisPessoa(p.nome), add: () => adicionarPessoa(p.id) }))
    : visitantes.map(v => ({ id: v.id, nome: v.nome, foto: null as string | null, iniciais: iniciaisVisitante(v.nome), add: () => adicionarVisitante(v.id) }))

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div className={styles.modal} onMouseDown={e => e.stopPropagation()} role="dialog" aria-modal="true">
        <span className={styles.grabber} aria-hidden="true" />
        <button className={styles.fechar} onClick={fechar} aria-label="Fechar"><X size={18} /></button>

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
          <Input
            id="busca-membro-celula"
            autoFocus
            placeholder={tab === 'pessoas' ? 'Buscar pessoa por nome' : 'Buscar visitante por nome'}
            value={busca}
            onChange={e => setBusca(e.target.value)}
            leftIcon={<Search size={18} />}
          />
        </div>

        <ul className={styles.listaResultados}>
          {lista.map(item => (
            <li key={item.id}
              className={clsx(styles.itemResultado, adicionando.has(item.id) && styles.itemResultadoSaindo)}
              role="button" tabIndex={0}
              onClick={item.add}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') item.add() }}
              title={`Adicionar à ${celula.singular.toLowerCase()}`}>
              {item.foto ? (
                <img src={item.foto} alt="" className={styles.avatar} />
              ) : (
                <span className={styles.avatarIniciais}>{item.iniciais}</span>
              )}
              <span className={styles.itemNome}>{item.nome}</span>
            </li>
          ))}
          {lista.length === 0 && (
            <li className={styles.semResultado}>
              {tab === 'pessoas' ? 'Nenhuma pessoa encontrada.' : 'Nenhum visitante encontrado.'}
            </li>
          )}
        </ul>

        <button className={styles.btnExterno} onClick={onCadastrarExterno}>
          <UserPlus size={16} /> Cadastrar visitante externo
        </button>
      </div>
    </div>
  )
}
