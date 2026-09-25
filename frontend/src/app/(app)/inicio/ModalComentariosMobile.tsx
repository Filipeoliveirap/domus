'use client'

import { useEffect, useRef, useState } from 'react'
import Image from 'next/image'
import { X, Heart, Send, Pencil, Trash2, Loader2, Building2 } from 'lucide-react'
import { clsx } from 'clsx'

import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAuthStore } from '@/store/authStore'
import { useComentarPostagem } from '@/hooks/postagem/useComentarPostagem'
import { useCurtirComentario } from '@/hooks/postagem/useCurtirComentario'
import { useDeletarComentario } from '@/hooks/postagem/useDeletarComentario'
import { ModalEditarComentario } from './ModalEditarComentario'
import { ModalConfirmacaoExclusao } from '@/components/common/ModalConfirmacaoExclusao/ModalConfirmacaoExclusao'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import type { Postagem, Comentario } from '@/types/postagem.type'

import styles from './ModalComentariosMobile.module.css'
import postItemStyles from './PostItem.module.css'

interface Props {
  postagem: Postagem
  aoFechar: () => void
}

export function ModalComentariosMobile({ postagem, aoFechar }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const comentar = useComentarPostagem()
  const curtirComentario = useCurtirComentario()
  const deletarComentario = useDeletarComentario()

  const meuId = useAuthStore((s) => s.id)
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const meuPessoaId = useAuthStore((s) => s.pessoaId) ?? meuId
  const role = useAuthStore((s) => s.role)
  const nomeUsuario = useAuthStore((s) => s.nome) ?? 'Você'
  const fotoIdUsuario = useAuthStore((s) => s.fotoId)

  const urlAvatarUsuario = urlFoto(fotoIdUsuario, 'THUMB')

  const ehAdminOuLider = role === 'ADMIN_IGREJA' || role === 'LIDER'
  const ehAutorPost = postagem.autor.id === meuPessoaId || postagem.autor.id === meuId
  const ehOutraIgrejaPost = postagem.igrejaAutor != null && postagem.igrejaAutor.id !== minhaIgrejaId

  const [novoComentario, setNovoComentario] = useState('')
  const [textoEnviando, setTextoEnviando] = useState<string | null>(null)
  const [comentarioEditarModal, setComentarioEditarModal] = useState<{ id: string; conteudo: string } | null>(null)
  const [confirmarExcluirId, setConfirmarExcluirId] = useState<string | null>(null)
  const [saindoComentarioId, setSaindoComentarioId] = useState<string | null>(null)
  const [respostaParaComentario, setRespostaParaComentario] = useState<{ id: string; autorNome: string } | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = ''
    }
  }, [])

  const handleComentar = (e: React.FormEvent) => {
    e.preventDefault()
    const texto = novoComentario.trim()
    if (!texto || comentar.isPending) return

    const paiId = respostaParaComentario?.id ?? null

    setTextoEnviando(texto)
    setNovoComentario('')
    setRespostaParaComentario(null)

    comentar.mutate(
      { postagemId: postagem.id, conteudo: texto, paiComentarioId: paiId },
      {
        onSuccess: () => setTextoEnviando(null),
        onError: () => {
          setNovoComentario(texto)
          setTextoEnviando(null)
        },
      },
    )
  }

  const handleExecutarExclusao = (comentarioId: string) => {
    setConfirmarExcluirId(null)
    setSaindoComentarioId(comentarioId)
    setTimeout(() => {
      deletarComentario.mutate({ postagemId: postagem.id, comentarioId }, {
        onSuccess: () => setSaindoComentarioId(null),
      })
    }, 240)
  }

  const jaFoiAdicionado = (postagem.comentariosRecentes ?? []).some((c) => c.conteudo === textoEnviando)
  const mostrandoPendente = (comentar.isPending || !!textoEnviando) && !jaFoiAdicionado

  return (
    <>
      <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
        <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
          <div className={styles.grabber} aria-hidden="true" />
          <div className={styles.modalHeader}>
            <h2 className={styles.modalTitulo}>Comentários ({postagem.totalComentarios})</h2>
            <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
              <X size={18} />
            </button>
          </div>

          <div className={styles.modalCorpo}>
            {(() => {
              const todosComentarios = postagem.comentariosRecentes ?? []
              const mapaComentarios = new Map<string, Comentario>()
              todosComentarios.forEach((c) => mapaComentarios.set(c.id, c))

              const mapaFilhos: Record<string, Comentario[]> = {}
              const comentariosRaiz: Comentario[] = []

              for (const c of todosComentarios) {
                if (c.paiComentarioId && mapaComentarios.has(c.paiComentarioId)) {
                  if (!mapaFilhos[c.paiComentarioId]) {
                    mapaFilhos[c.paiComentarioId] = []
                  }
                  mapaFilhos[c.paiComentarioId].push(c)
                } else {
                  comentariosRaiz.push(c)
                }
              }

              const comentariosOrdenados: { comentario: Comentario; nivel: number }[] = []

              const visitar = (id: string, nivel: number) => {
                const filhos = (mapaFilhos[id] ?? []).sort(
                  (a, b) => new Date(b.criadoEm).getTime() - new Date(a.criadoEm).getTime(),
                )
                for (const f of filhos) {
                  comentariosOrdenados.push({ comentario: f, nivel })
                  visitar(f.id, nivel + 1)
                }
              }

              const comentariosRaizOrdenados = [...comentariosRaiz].sort(
                (a, b) => new Date(b.criadoEm).getTime() - new Date(a.criadoEm).getTime(),
              )
              for (const r of comentariosRaizOrdenados) {
                comentariosOrdenados.push({ comentario: r, nivel: 0 })
                visitar(r.id, 1)
              }

              return comentariosOrdenados.map(({ comentario: c, nivel }) => {
                const urlAvatarComentario = urlFoto(c.autor.fotoId, 'THUMB')
                const ehAutorComentario = c.autor.id === meuPessoaId || c.autor.id === meuId || (!!nomeUsuario && c.autor.nome === nomeUsuario)
                const podeGerenciarComentario = c.podeDeletar ?? (ehAutorComentario || ehAutorPost || (ehAdminOuLider && !ehOutraIgrejaPost))
                const ehOutraIgrejaComentario = c.igrejaAutor != null && c.igrejaAutor.id !== minhaIgrejaId
                const estaSaindoEste = saindoComentarioId === c.id
                const ehResposta = Boolean(c.paiComentarioId)
                const comentarioPai = ehResposta ? todosComentarios.find((p) => p.id === c.paiComentarioId) : null
                const estaSendoRespondido = respostaParaComentario?.id === c.id

                return (
                  <div
                    key={c.id}
                    className={`${postItemStyles.itemComentario} ${
                      estaSendoRespondido ? postItemStyles.itemComentarioEmFoco : ''
                    } ${estaSaindoEste ? postItemStyles.saindoComentario : ''}`}
                    style={{
                      marginLeft: nivel > 0 ? `${Math.min(nivel, 3) * 16}px` : undefined,
                      borderLeft: nivel > 0 ? '2px solid var(--border-color, rgba(115, 118, 134, 0.25))' : undefined,
                      background: nivel > 0 ? 'rgba(250, 248, 255, 0.75)' : undefined,
                    }}
                  >
                    <div className={postItemStyles.avatarComentario}>
                      {urlAvatarComentario ? (
                        <Image src={urlAvatarComentario} alt="" width={24} height={24} unoptimized style={{ borderRadius: '50%' }} />
                      ) : (
                        iniciais(c.autor.nome)
                      )}
                    </div>

                    <div className={postItemStyles.corpoComentario}>
                      <div className={postItemStyles.cabecalhoComentario}>
                        <span className={postItemStyles.autorComentario}>
                          {doisPrimeirosNomes(c.autor.nome)}
                          {ehOutraIgrejaComentario && (
                            <span className={postItemStyles.badgeIgrejaComentario} title={c.igrejaAutor?.nome}>
                              <Building2 size={11} aria-hidden="true" />
                              {c.igrejaAutor?.sigla ?? c.igrejaAutor?.nome}
                            </span>
                          )}
                          {comentarioPai && (
                            <span className={postItemStyles.tagRespondendoAutor}>
                              {' '}em resposta a @{doisPrimeirosNomes(comentarioPai.autor.nome)}
                            </span>
                          )}
                        </span>
                        {podeGerenciarComentario && (
                          <div className={postItemStyles.acoesComentario}>
                            {ehAutorComentario && (
                              <button
                                type="button"
                                className={postItemStyles.btnAcaoComentario}
                                onClick={() => setComentarioEditarModal({ id: c.id, conteudo: c.conteudo })}
                              >
                                <Pencil size={11} />
                              </button>
                            )}
                            <button
                              type="button"
                              className={`${postItemStyles.btnAcaoComentario} ${postItemStyles.btnExcluirComentario}`}
                              onClick={() => setConfirmarExcluirId(c.id)}
                            >
                              <Trash2 size={11} />
                            </button>
                          </div>
                        )}
                      </div>

                      <span className={postItemStyles.textoComentario}>{c.conteudo}</span>

                      <div className={postItemStyles.rodapeComentario}>
                        <button
                          type="button"
                          className={`${postItemStyles.btnInteracaoComentario} ${
                            c.curtidoPorMim ? postItemStyles.btnCurtidoComentario : ''
                          }`}
                          onClick={() => curtirComentario.mutate({ postagemId: postagem.id, comentarioId: c.id })}
                        >
                          <Heart size={12} fill={c.curtidoPorMim ? 'currentColor' : 'none'} />
                          {c.totalCurtidas > 0 ? c.totalCurtidas : 'Curtir'}
                        </button>

                        <button
                          type="button"
                          className={postItemStyles.btnInteracaoComentario}
                          onClick={() => {
                            setRespostaParaComentario({ id: c.id, autorNome: c.autor.nome })
                            setTimeout(() => inputRef.current?.focus(), 50)
                          }}
                        >
                          Responder
                        </button>
                      </div>
                    </div>
                  </div>
                )
              })
            })()}

            {mostrandoPendente && (
              <div className={postItemStyles.itemComentarioEnviando}>
                <div className={postItemStyles.avatarComentario}>
                  {urlAvatarUsuario ? (
                    <Image src={urlAvatarUsuario} alt="" width={24} height={24} unoptimized style={{ borderRadius: '50%' }} />
                  ) : (
                    iniciais(nomeUsuario)
                  )}
                </div>
                <div className={postItemStyles.corpoComentarioEnviando}>
                  <div className={postItemStyles.cabecalhoComentarioEnviando}>
                    <span className={postItemStyles.autorComentario}>{doisPrimeirosNomes(nomeUsuario)}</span>
                    <span className={postItemStyles.badgeEnviando}>Enviando...</span>
                  </div>
                  {textoEnviando ? (
                    <span className={postItemStyles.textoComentario}>{textoEnviando}</span>
                  ) : (
                    <Skeleton width="60%" height="13px" radius="4px" />
                  )}
                </div>
              </div>
            )}
          </div>

          <div className={styles.modalRodape}>
            {respostaParaComentario && (
              <div className={postItemStyles.badgeRespostaTag}>
                <span>Respondendo a @{doisPrimeirosNomes(respostaParaComentario.autorNome)}</span>
                <button type="button" className={postItemStyles.btnCancelarResposta} onClick={() => setRespostaParaComentario(null)}>
                  <X size={12} />
                </button>
              </div>
            )}

            <form className={postItemStyles.formComentario} onSubmit={handleComentar}>
              <input
                ref={inputRef}
                type="text"
                className={postItemStyles.inputComentario}
                placeholder={respostaParaComentario ? `Respondendo a @${doisPrimeirosNomes(respostaParaComentario.autorNome)}...` : 'Escreva um comentário...'}
                value={novoComentario}
                onChange={(e) => setNovoComentario(e.target.value)}
                disabled={comentar.isPending}
              />
              <button
                type="submit"
                className={`${postItemStyles.btnAmem} ${comentar.isPending ? postItemStyles.btnComentarioEnviando : ''}`}
                disabled={comentar.isPending || (!novoComentario.trim() && !textoEnviando)}
              >
                {comentar.isPending ? <Loader2 size={12} className={postItemStyles.spinnerIcone} /> : <Send size={12} />}
              </button>
            </form>
          </div>
        </div>
      </div>

      {comentarioEditarModal && (
        <ModalEditarComentario
          postagemId={postagem.id}
          comentarioId={comentarioEditarModal.id}
          conteudoInicial={comentarioEditarModal.conteudo}
          aoFechar={() => setComentarioEditarModal(null)}
        />
      )}

      {confirmarExcluirId && (
        <ModalConfirmacaoExclusao
          titulo="Excluir Comentário"
          mensagem="Tem certeza que deseja remover este comentário?"
          carregando={deletarComentario.isPending}
          onConfirmar={() => handleExecutarExclusao(confirmarExcluirId)}
          onClose={() => setConfirmarExcluirId(null)}
        />
      )}
    </>
  )
}
