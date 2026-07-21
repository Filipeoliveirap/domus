'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { Search, X, Check } from 'lucide-react'
import { useMembros } from '@/hooks/membro/useMembros'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useInscreverMembros } from '@/hooks/inscricao/useInscreverMembros'
import { useDebounce } from '@/hooks/useDebounce'
import { iniciais, rotuloStatus } from '@/lib/formats/membroFormat'
import type { MembroResponse } from '@/types/membro.type'
import styles from './ModalInscreverMembros.module.css'

interface Props {
  eventoId: string
  tituloEvento: string
  /** Evento exclusivo para membros: visitantes e inativos não podem ser inscritos. */
  exclusivoMembros: boolean
  /** Evento exclusivo para batizados: quem não estiver marcado como batizado não pode ser inscrito. */
  exclusivoBatizados: boolean
  onClose: () => void
}

/**
 * F9/F10 — motivo (se houver) para um membro aparecer desabilitado na lista. Checado nesta
 * ordem: já inscrito é o motivo mais concreto (o clique falharia com "já inscrito"); só
 * depois vêm as regras de elegibilidade do evento (membros/batizados).
 */
function motivoBloqueio(
  m: MembroResponse,
  jaInscritos: Set<string>,
  exclusivoMembros: boolean,
  exclusivoBatizados: boolean,
): string | null {
  if (jaInscritos.has(m.id)) return 'Já inscrito neste evento'
  if (exclusivoMembros && m.status === 'VISITANTE') return 'Visitante — evento exclusivo para membros'
  if (exclusivoMembros && m.status === 'INATIVO') return 'Inativo — evento exclusivo para membros'
  if (exclusivoBatizados && !m.batizado) return 'Não batizado — evento exclusivo para batizados'
  return null
}

/**
 * Busca + seleção múltipla de membros para inscrever no evento. Deliberadamente NÃO tem
 * "selecionar todos" — a seleção um a um é o que evita uma inscrição em massa por engano.
 */
export function ModalInscreverMembros({
  eventoId, tituloEvento, exclusivoMembros, exclusivoBatizados, onClose,
}: Props) {
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState<Set<string>>(new Set())
  const inputRef = useRef<HTMLInputElement>(null)

  const buscaDebounced = useDebounce(busca, 300)
  const { data, isLoading } = useMembros({ q: buscaDebounced, page: 0, size: 30 })
  const membros = data?.content ?? []

  // F9: quem já está inscrito precisa aparecer desabilitado. `useParticipantes` é a lista
  // reduzida que QUALQUER membro autenticado pode chamar — a completa (`useListaInscritos`)
  // é restrita a ADMIN/LÍDER e devolveria 401 para um membro comum abrindo este modal.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const jaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.membroId)),
    [participantes],
  )

  const inscreverMembros = useInscreverMembros(eventoId)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !inscreverMembros.isPending) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, inscreverMembros.isPending])

  function alternarSelecao(membroId: string) {
    setSelecionados((atual) => {
      const novo = new Set(atual)
      if (novo.has(membroId)) novo.delete(membroId)
      else novo.add(membroId)
      return novo
    })
  }

  function aoConfirmar() {
    inscreverMembros.mutate(Array.from(selecionados), {
      onSuccess: () => onClose(),
    })
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !inscreverMembros.isPending && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-inscrever-membros"
      >
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo} id="titulo-inscrever-membros">Inscrever membros</h2>
            <p className={styles.subtitulo}>{tituloEvento}</p>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={inscreverMembros.isPending}
          >
            <X size={20} />
          </button>
        </div>

        <div className={styles.buscaWrap}>
          <Search size={16} className={styles.buscaIcone} />
          <input
            ref={inputRef}
            type="text"
            className={styles.buscaInput}
            placeholder="Buscar por nome…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
        </div>

        <div className={styles.lista}>
          {isLoading ? (
            <p className={styles.estado}>Carregando membros…</p>
          ) : membros.length === 0 ? (
            <p className={styles.estado}>Nenhum membro encontrado.</p>
          ) : (
            membros.map((m) => {
              const motivo = motivoBloqueio(m, jaInscritos, exclusivoMembros, exclusivoBatizados)
              const bloqueado = !!motivo
              const marcado = selecionados.has(m.id)
              return (
                <label
                  key={m.id}
                  className={[
                    styles.linha,
                    marcado ? styles.linhaSelecionada : '',
                    bloqueado ? styles.linhaBloqueada : '',
                  ].join(' ')}
                >
                  <input
                    type="checkbox"
                    className={styles.checkbox}
                    checked={marcado}
                    disabled={bloqueado}
                    onChange={() => alternarSelecao(m.id)}
                  />
                  <span className={styles.avatar}>
                    {m.foto ? (
                      // eslint-disable-next-line @next/next/no-img-element -- URL de storage externo
                      <img src={m.foto} alt="" className={styles.avatarFoto} />
                    ) : (
                      iniciais(m.nome)
                    )}
                  </span>
                  <span className={styles.info}>
                    <span className={styles.nome}>{m.nome}</span>
                    <span className={styles.detalhe}>
                      {motivo ?? `${rotuloStatus(m.status)} · ${m.ministerio || 'sem ministério'}`}
                    </span>
                  </span>
                  {marcado && <Check size={16} className={styles.checkIcone} aria-hidden="true" />}
                </label>
              )
            })
          )}
        </div>

        <div className={styles.footer}>
          <span className={styles.contador}>
            {selecionados.size} selecionado{selecionados.size === 1 ? '' : 's'}
          </span>
          <div className={styles.footerAcoes}>
            <button
              type="button"
              className={styles.btnCancelar}
              onClick={onClose}
              disabled={inscreverMembros.isPending}
            >
              Cancelar
            </button>
            <button
              type="button"
              className={styles.btnConfirmar}
              onClick={aoConfirmar}
              disabled={selecionados.size === 0 || inscreverMembros.isPending}
            >
              {inscreverMembros.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
