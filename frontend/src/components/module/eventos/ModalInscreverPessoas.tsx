'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import { Search, X, Check, AlertTriangle } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useInscreverPessoas } from '@/hooks/inscricao/useInscreverPessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { iniciais, rotuloVinculo } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import type { PessoaResponse } from '@/types/pessoa.type'
import type { Impedimento } from '@/types/inscricao.type'
import styles from './ModalInscreverPessoas.module.css'

interface Props {
  eventoId: string
  tituloEvento: string
  /** Evento exclusivo para membros: só quem tem vínculo MEMBRO pode ser inscrito. */
  exclusivoMembros: boolean
  onClose: () => void
  /** Usado dentro de ModalInscreverAlguem (aba "Pessoas da igreja") — sem overlay nem
   *  cabeçalho próprios, porque o modal pai já mostra os dois. */
  embutido?: boolean
}

function jaInscrita(p: PessoaResponse, jaInscritos: Set<string>): boolean {
  return jaInscritos.has(p.id)
}

// Não bloqueia: EXCLUSIVO_MEMBROS é contornável pelo backend para quem gerencia.
function avisoElegibilidade(p: PessoaResponse, exclusivoMembros: boolean): string | null {
  if (exclusivoMembros && p.vinculo !== 'MEMBRO') {
    return 'Congregante — evento exclusivo para membros'
  }
  return null
}

// Sem "selecionar todos" de propósito: evita inscrição em massa por engano.
export function ModalInscreverPessoas({
  eventoId, tituloEvento, exclusivoMembros, onClose, embutido = false,
}: Props) {
  const [busca, setBusca] = useState('')
  const [selecionados, setSelecionados] = useState<Set<string>>(new Set())
  // Impedimentos contornáveis devolvidos pelo 422 — abre a confirmação "inscrever mesmo
  // assim" só para quem gerencia. `null` = confirmação fechada.
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const role = useAuthStore((s) => s.role)
  const ehGestor = podeGerenciarInscricoes(role)

  const buscaDebounced = useDebounce(busca, 300)
  const { data, isLoading } = usePessoas({ q: buscaDebounced, page: 0, size: 30 })
  const pessoas = data?.content ?? []

  // Quem já está inscrito precisa aparecer desabilitado. `useParticipantes` é a lista
  // reduzida que QUALQUER pessoa autenticada pode chamar — a completa (`useListaInscritos`)
  // é restrita a ADMIN/LÍDER e devolveria 401 para uma pessoa comum abrindo este modal.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const jaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.pessoaId).filter((id): id is string => id !== null)),
    [participantes],
  )

  // Só o gestor pode contornar, então só ele passa o callback. Para os demais, o hook
  // notifica o 422 normalmente (era aqui que o erro sumia em silêncio antes).
  const inscreverPessoas = useInscreverPessoas(eventoId, {
    onContornavel: ehGestor
      ? (impedimentos) => setImpedimentosParaConfirmar(impedimentos)
      : undefined,
  })

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !inscreverPessoas.isPending) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, inscreverPessoas.isPending])

  function alternarSelecao(pessoaId: string) {
    setSelecionados((atual) => {
      const novo = new Set(atual)
      if (novo.has(pessoaId)) novo.delete(pessoaId)
      else novo.add(pessoaId)
      return novo
    })
  }

  function aoConfirmar() {
    const pessoaIds = Array.from(selecionados)
    // O tratamento de erro vive no hook: contorno abre a confirmação (via onContornavel),
    // o resto é notificado. Aqui só o caminho de sucesso.
    inscreverPessoas.mutate({ pessoaIds }, { onSuccess: () => onClose() })
  }

  /** "Inscrever mesmo assim": reenvia com `confirmado=true` (só surte efeito para gestor). */
  function aoConfirmarMesmoAssim() {
    const pessoaIds = Array.from(selecionados)
    inscreverPessoas.mutate({ pessoaIds, confirmado: true }, {
      onSuccess: () => {
        setImpedimentosParaConfirmar(null)
        onClose()
      },
      onError: () => setImpedimentosParaConfirmar(null),
    })
  }

  const conteudo = (
    <>
        {!embutido && (
          <div className={styles.header}>
            <div>
              <h2 className={styles.titulo} id="titulo-inscrever-pessoas">Inscrever pessoas</h2>
              <p className={styles.subtitulo}>{tituloEvento}</p>
            </div>
            <button
              type="button"
              className={styles.btnFechar}
              onClick={onClose}
              aria-label="Fechar"
              disabled={inscreverPessoas.isPending}
            >
              <X size={20} />
            </button>
          </div>
        )}

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
            <p className={styles.estado}>Carregando pessoas…</p>
          ) : pessoas.length === 0 ? (
            <p className={styles.estado}>Nenhuma pessoa encontrada.</p>
          ) : (
            pessoas.map((p) => {
              const bloqueado = jaInscrita(p, jaInscritos)
              const aviso = !bloqueado ? avisoElegibilidade(p, exclusivoMembros) : null
              const marcado = selecionados.has(p.id)
              return (
                <label
                  key={p.id}
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
                    onChange={() => alternarSelecao(p.id)}
                  />
                  <span className={styles.avatar}>
                    {urlFoto(p.fotoId, 'THUMB') ? (
                      <Image src={urlFoto(p.fotoId, 'THUMB')!} alt="" width={36} height={36} unoptimized className={styles.avatarFoto} />
                    ) : (
                      iniciais(p.nome)
                    )}
                  </span>
                  <span className={styles.info}>
                    <span className={styles.nome}>{p.nome}</span>
                    <span className={styles.detalhe}>
                      {bloqueado
                        ? 'Já inscrita neste evento'
                        : (aviso ?? rotuloVinculo(p.vinculo))}
                    </span>
                  </span>
                  {aviso && !bloqueado && (
                    <AlertTriangle
                      size={15}
                      className={styles.avisoIcone}
                      aria-label="Pode não ser elegível para este evento"
                    />
                  )}
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
              disabled={inscreverPessoas.isPending}
            >
              Cancelar
            </button>
            <button
              type="button"
              className={styles.btnConfirmar}
              onClick={aoConfirmar}
              disabled={selecionados.size === 0 || inscreverPessoas.isPending}
            >
              {inscreverPessoas.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </div>
        </div>
    </>
  )

  return (
    <>
    {embutido ? conteudo : (
      <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
        <div
          className={styles.modal}
          onMouseDown={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-labelledby="titulo-inscrever-pessoas"
        >
          {conteudo}
        </div>
      </div>
    )}

    {impedimentosParaConfirmar && (
      <ModalConfirmacao
        titulo="Inscrever mesmo assim?"
        textoConfirmar="Inscrever mesmo assim"
        isLoading={inscreverPessoas.isPending}
        onConfirmar={aoConfirmarMesmoAssim}
        onClose={() => setImpedimentosParaConfirmar(null)}
        mensagem={
          <>
            <p>
              {selecionados.size === 1 ? 'Esta pessoa não atende' : 'Uma ou mais pessoas selecionadas não atendem'}
              {' '}a todos os requisitos deste evento:
            </p>
            <ul>
              {impedimentosParaConfirmar.map((imp) => (
                <li key={imp.codigo}>{imp.mensagem}</li>
              ))}
            </ul>
          </>
        }
      />
    )}
    </>
  )
}
