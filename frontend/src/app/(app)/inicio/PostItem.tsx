'use client'

import { useRef, useState } from 'react'
import Image from 'next/image'
import { Heart, MessageSquare, Send, Loader2, MoreVertical, Pencil, Trash2, Check, X, Building2, Globe } from 'lucide-react'

import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { useAuthStore } from '@/store/authStore'
import { useClickFora } from '@/hooks/useClickFora'
import { useCurtirPostagem } from '@/hooks/postagem/useCurtirPostagem'
import { useComentarPostagem } from '@/hooks/postagem/useComentarPostagem'
import { useDeletarPostagem } from '@/hooks/postagem/useDeletarPostagem'
import { useAtualizarPostagem } from '@/hooks/postagem/useAtualizarPostagem'
import { useDeletarComentario } from '@/hooks/postagem/useDeletarComentario'
import { useAtualizarComentario } from '@/hooks/postagem/useAtualizarComentario'
import { useCurtirComentario } from '@/hooks/postagem/useCurtirComentario'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { Colapsavel } from '@/components/common/Transicao/Colapsavel'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { ModalEditarPostagem } from './ModalEditarPostagem'
import { ModalEditarComentario } from './ModalEditarComentario'
import { ModalComentariosMobile } from './ModalComentariosMobile'
import { ModalConfirmacaoExclusao } from '@/components/common/ModalConfirmacaoExclusao/ModalConfirmacaoExclusao'
import { ModalPerfilResumo, type PerfilResumoDados, type PosicaoTarget } from '@/components/common/ModalPerfilResumo/ModalPerfilResumo'
import { DrawerDetalhePessoa } from '@/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa'
import { notificar } from '@/components/common/Notificacao/notificar'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import type { Postagem, Comentario } from '@/types/postagem.type'

import styles from './PostItem.module.css'

export function PostItem({ postagem }: { postagem: Postagem }) {
  const { congregacao, concordar } = useRotulos()
  const curtir = useCurtirPostagem()
  const curtirComentario = useCurtirComentario()
  const comentar = useComentarPostagem()
  const deletarPost = useDeletarPostagem()
  const atualizarPost = useAtualizarPostagem()
  const deletarComentario = useDeletarComentario()
  const atualizarComentario = useAtualizarComentario()

  const meuId = useAuthStore((s) => s.id)
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)
  const meuPessoaId = useAuthStore((s) => s.pessoaId) ?? meuId
  const role = useAuthStore((s) => s.role)
  const nomeUsuario = useAuthStore((s) => s.nome) ?? 'Você'
  const fotoIdUsuario = useAuthStore((s) => s.fotoId)

  const urlAvatarUsuario = urlFoto(fotoIdUsuario, 'THUMB')

  const ehAdminOuLider = role === 'ADMIN_IGREJA' || role === 'LIDER'
  const ehAutorPost = postagem.autor.id === meuPessoaId || postagem.autor.id === meuId || (!!nomeUsuario && postagem.autor.nome === nomeUsuario)
  const ehOutraIgrejaPost = postagem.igrejaAutor != null && postagem.igrejaAutor.id !== minhaIgrejaId
  const podeGerenciarPost = postagem.podeEditar ?? (ehAutorPost || (ehAdminOuLider && !ehOutraIgrejaPost))

  const [comentariosAbertos, setComentariosAbertos] = useState(false)
  const [modalComentariosMobile, setModalComentariosMobile] = useState(false)
  const [novoComentario, setNovoComentario] = useState('')
  const [textoEnviando, setTextoEnviando] = useState<string | null>(null)
  const [fotoModal, setFotoModal] = useState<{ id: string; descricao: string } | null>(null)
  const [perfilResumo, setPerfilResumo] = useState<PerfilResumoDados | null>(null)
  const [posicaoTarget, setPosicaoTarget] = useState<PosicaoTarget | null>(null)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)

  // Modais de ação
  const [modalEditarAberto, setModalEditarAberto] = useState(false)
  const [confirmarExcluirPost, setConfirmarExcluirPost] = useState(false)
  const [saindoPost, setSaindoPost] = useState(false)
  const [confirmarExcluirComentarioId, setConfirmarExcluirComentarioId] = useState<string | null>(null)
  const [saindoComentarioId, setSaindoComentarioId] = useState<string | null>(null)

  // Menu post
  const [menuPostAberto, setMenuPostAberto] = useState(false)
  const menuPostRef = useRef<HTMLDivElement>(null)
  useClickFora(menuPostRef, () => setMenuPostAberto(false))

  // Edit e resposta comentario
  const [comentarioEditarModal, setComentarioEditarModal] = useState<{ id: string; conteudo: string } | null>(null)
  const [respostaParaComentario, setRespostaParaComentario] = useState<{ id: string; autorNome: string } | null>(null)
  const inputComentarioRef = useRef<HTMLInputElement>(null)

  const url = urlFoto(postagem.autor.fotoId, 'THUMB')
  const urlFotoPost = urlFoto(postagem.fotoId, 'DISPLAY')

  const handleCurtir = () => {
    curtir.mutate({ postagemId: postagem.id, tipo: 'AMEM' })
  }

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
        onSuccess: () => {
          setTextoEnviando(null)
        },
        onError: () => {
          setNovoComentario(texto)
          setTextoEnviando(null)
        },
      },
    )
  }

  const handleExecutarExclusaoPost = () => {
    setConfirmarExcluirPost(false)
    setSaindoPost(true)
    setTimeout(() => {
      deletarPost.mutate(postagem.id, {
        onSuccess: () => {
          notificar.sucesso('Postagem excluída', 'A postagem foi removida com sucesso.')
        },
      })
    }, 280)
  }

  const handleExecutarExclusaoComentario = (comentarioId: string) => {
    setConfirmarExcluirComentarioId(null)
    setSaindoComentarioId(comentarioId)
    setTimeout(() => {
      deletarComentario.mutate(
        { postagemId: postagem.id, comentarioId },
        {
          onSuccess: () => {
            setSaindoComentarioId(null)
          },
        },
      )
    }, 240)
  }

  const abrirPerfilAutor = (e: React.MouseEvent) => {
    e.stopPropagation()
    const rect = e.currentTarget.getBoundingClientRect()
    setPosicaoTarget({
      top: rect.top,
      left: rect.left,
      bottom: rect.bottom,
      right: rect.right,
      width: rect.width,
      height: rect.height,
    })
    setPerfilResumo({
      id: postagem.autor.id,
      nome: postagem.autor.nome,
      fotoId: postagem.autor.fotoId,
      cargo: postagem.autor.cargo,
      igreja: postagem.igrejaAutor,
    })
  }

  const abrirPerfilComentario = (
    e: React.MouseEvent,
    autor: { id?: string; nome: string; fotoId?: string | null; cargo?: string | null },
    igrejaAutor?: { id?: string; nome: string; sigla?: string | null } | null,
  ) => {
    e.stopPropagation()
    const rect = e.currentTarget.getBoundingClientRect()
    setPosicaoTarget({
      top: rect.top,
      left: rect.left,
      bottom: rect.bottom,
      right: rect.right,
      width: rect.width,
      height: rect.height,
    })
    setPerfilResumo({
      id: autor.id,
      nome: autor.nome,
      fotoId: autor.fotoId,
      cargo: autor.cargo,
      igreja: igrejaAutor,
    })
  }

  const abrirFotoPost = () => {
    if (postagem.fotoId) {
      setFotoModal({ id: postagem.fotoId, descricao: 'Foto da postagem' })
    }
  }

  const jaFoiAdicionado = (postagem.comentariosRecentes ?? []).some(
    (c) => c.conteudo === textoEnviando,
  )
  const mostrandoPendente = (comentar.isPending || !!textoEnviando) && !jaFoiAdicionado

  return (
    <article className={`${styles.postCard} ${saindoPost ? styles.saindoPost : ''}`}>
      <div className={styles.cabecalho}>
        <div className={styles.autorBloco}>
          <div
            className={`${styles.avatar} ${styles.avatarClicavel}`}
            onClick={abrirPerfilAutor}
            role="button"
            tabIndex={0}
          >
            {url ? (
              <Image src={url} alt="" width={40} height={40} unoptimized style={{ borderRadius: '50%' }} />
            ) : (
              iniciais(postagem.autor.nome)
            )}
          </div>

          <div className={styles.autorInfo}>
            <div className={styles.autorLinhaNome}>
              <span className={styles.nomeAutor} onClick={abrirPerfilAutor} role="button" tabIndex={0} style={{ cursor: 'pointer' }}>
                {doisPrimeirosNomes(postagem.autor.nome)}
              </span>
            </div>
            <span className={styles.metaPost}>
              {new Date(postagem.criadoEm).toLocaleDateString('pt-BR')}
            </span>
          </div>
        </div>

        <div className={styles.headerAcoes}>
          <div className={styles.tagsContainer}>
            {postagem.restritoPropriaIgreja === false && (
              <span
                className={styles.tagRede}
                title={`Compartilhado com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`}
              >
                <Globe size={11} aria-hidden="true" />
                {congregacao.plural}
              </span>
            )}
            <span className={styles.tagPost}>{postagem.tipo.replace('_', ' ')}</span>
          </div>
          {podeGerenciarPost && (
            <div className={styles.menuWrapper} ref={menuPostRef}>
              <button
                type="button"
                className={styles.btnMenu3Pontos}
                onClick={() => setMenuPostAberto((v) => !v)}
                aria-label="Opções da postagem"
              >
                <MoreVertical size={16} />
              </button>
              {menuPostAberto && (
                <div className={styles.dropdownMenu}>
                  <button
                    type="button"
                    className={styles.dropdownItem}
                    onClick={() => {
                      setMenuPostAberto(false)
                      setModalEditarAberto(true)
                    }}
                  >
                    <Pencil size={14} />
                    Editar
                  </button>
                  <button
                    type="button"
                    className={`${styles.dropdownItem} ${styles.dropdownItemExcluir}`}
                    onClick={() => {
                      setMenuPostAberto(false)
                      setConfirmarExcluirPost(true)
                    }}
                  >
                    <Trash2 size={14} />
                    Excluir
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {postagem.conteudo && postagem.conteudo.trim() && (
        <p className={styles.conteudo}>{postagem.conteudo}</p>
      )}

      {postagem.fotoId && urlFotoPost && (
        <div className={styles.fotoContainer} onClick={abrirFotoPost} role="button" tabIndex={0}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={urlFotoPost}
            alt="Foto da postagem"
            className={styles.postImagem}
            loading="lazy"
          />
        </div>
      )}

      {postagem.versiculoRef && postagem.conteudo && (
        <div className={styles.blocoVersiculo}>
          &ldquo;{postagem.conteudo.slice(0, 100)}...&rdquo;
          <span className={styles.refVersiculo}>— {postagem.versiculoRef}</span>
        </div>
      )}

      <div className={styles.barraReacoes}>
        <div className={styles.grupoCurtidas}>
          <button
            type="button"
            className={`${styles.btnAmem} ${postagem.minhaReacao ? styles.btnAmemAtivo : ''}`}
            onClick={handleCurtir}
          >
            <Heart size={15} fill={postagem.minhaReacao ? 'currentColor' : 'none'} />
            Amém ({postagem.totalCurtidas})
          </button>
        </div>

        <button
          type="button"
          className={styles.btnComentarToggle}
          onClick={() => {
            if (window.innerWidth < 768) {
              setModalComentariosMobile(true)
            } else {
              setComentariosAbertos(!comentariosAbertos)
            }
          }}
        >
          <MessageSquare size={15} />
          {postagem.totalComentarios} comentários
        </button>
      </div>

      <Colapsavel aberto={comentariosAbertos}>
        <div className={styles.secaoComentarios}>
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
                  className={`${styles.itemComentario} ${
                    estaSendoRespondido ? styles.itemComentarioEmFoco : ''
                  } ${estaSaindoEste ? styles.saindoComentario : ''}`}
                  style={{
                    marginLeft: nivel > 0 ? `${Math.min(nivel, 3) * 20}px` : undefined,
                    borderLeft: nivel > 0 ? '2px solid var(--border-color, rgba(115, 118, 134, 0.25))' : undefined,
                    background: nivel > 0 ? 'rgba(250, 248, 255, 0.75)' : undefined,
                  }}
                >
                  <div
                    className={`${styles.avatarComentario} ${styles.avatarClicavel}`}
                    onClick={(e) => abrirPerfilComentario(e, c.autor, c.igrejaAutor)}
                    role="button"
                    tabIndex={0}
                    style={{ cursor: 'pointer' }}
                  >
                    {urlAvatarComentario ? (
                      <Image
                        src={urlAvatarComentario}
                        alt=""
                        width={24}
                        height={24}
                        unoptimized
                        style={{ borderRadius: '50%' }}
                      />
                    ) : (
                      iniciais(c.autor.nome)
                    )}
                  </div>

                  <div className={styles.corpoComentario}>
                    <div className={styles.cabecalhoComentario}>
                      <span
                        className={styles.autorComentario}
                        onClick={(e) => abrirPerfilComentario(e, c.autor, c.igrejaAutor)}
                        role="button"
                        tabIndex={0}
                        style={{ cursor: 'pointer' }}
                      >
                        {doisPrimeirosNomes(c.autor.nome)}
                        {ehOutraIgrejaComentario && (
                          <span className={styles.badgeIgrejaComentario} title={c.igrejaAutor?.nome}>
                            <Building2 size={11} aria-hidden="true" />
                            {c.igrejaAutor?.sigla ?? c.igrejaAutor?.nome}
                          </span>
                        )}
                        {comentarioPai && (
                          <span className={styles.tagRespondendoAutor}>
                            {' '}em resposta a @{doisPrimeirosNomes(comentarioPai.autor.nome)}
                          </span>
                        )}
                      </span>
                      {podeGerenciarComentario && (
                        <div className={styles.acoesComentario}>
                          {ehAutorComentario && (
                            <button
                              type="button"
                              className={styles.btnAcaoComentario}
                              title="Editar comentário"
                              onClick={() =>
                                setComentarioEditarModal({ id: c.id, conteudo: c.conteudo })
                              }
                            >
                              <Pencil size={11} />
                            </button>
                          )}
                          <button
                            type="button"
                            className={`${styles.btnAcaoComentario} ${styles.btnExcluirComentario}`}
                            title="Excluir comentário"
                            onClick={() => setConfirmarExcluirComentarioId(c.id)}
                          >
                            <Trash2 size={11} />
                          </button>
                        </div>
                      )}
                    </div>

                    <span className={styles.textoComentario}>{c.conteudo}</span>

                    <div className={styles.rodapeComentario}>
                      <button
                        type="button"
                        className={`${styles.btnInteracaoComentario} ${
                          c.curtidoPorMim ? styles.btnCurtidoComentario : ''
                        }`}
                        onClick={() =>
                          curtirComentario.mutate({
                            postagemId: postagem.id,
                            comentarioId: c.id,
                          })
                        }
                      >
                        <Heart
                          size={12}
                          fill={c.curtidoPorMim ? 'currentColor' : 'none'}
                        />
                        {c.totalCurtidas > 0 ? c.totalCurtidas : 'Curtir'}
                      </button>

                      <button
                        type="button"
                        className={styles.btnInteracaoComentario}
                        onClick={() => {
                          setRespostaParaComentario({
                            id: c.id,
                            autorNome: c.autor.nome,
                          })
                          setTimeout(() => inputComentarioRef.current?.focus(), 50)
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
            <div className={styles.itemComentarioEnviando}>
              <div className={styles.avatarComentario}>
                {urlAvatarUsuario ? (
                  <Image
                    src={urlAvatarUsuario}
                    alt=""
                    width={24}
                    height={24}
                    unoptimized
                    style={{ borderRadius: '50%' }}
                  />
                ) : (
                  iniciais(nomeUsuario)
                )}
              </div>
              <div className={styles.corpoComentarioEnviando}>
                <div className={styles.cabecalhoComentarioEnviando}>
                  <span className={styles.autorComentario}>{doisPrimeirosNomes(nomeUsuario)}</span>
                  <span className={styles.badgeEnviando}>Enviando...</span>
                </div>
                {textoEnviando ? (
                  <span className={styles.textoComentario}>{textoEnviando}</span>
                ) : (
                  <Skeleton width="60%" height="13px" radius="4px" />
                )}
              </div>
            </div>
          )}

          {respostaParaComentario && (
            <div className={styles.badgeRespostaTag}>
              <span>Respondendo a @{doisPrimeirosNomes(respostaParaComentario.autorNome)}</span>
              <button
                type="button"
                className={styles.btnCancelarResposta}
                onClick={() => setRespostaParaComentario(null)}
              >
                <X size={12} />
              </button>
            </div>
          )}

          <form className={styles.formComentario} onSubmit={handleComentar}>
            <input
              ref={inputComentarioRef}
              type="text"
              className={styles.inputComentario}
              placeholder={
                respostaParaComentario
                  ? `Respondendo a @${doisPrimeirosNomes(respostaParaComentario.autorNome)}...`
                  : 'Escreva um comentário...'
              }
              value={novoComentario}
              onChange={(e) => setNovoComentario(e.target.value)}
              disabled={comentar.isPending}
            />
            <button
              type="submit"
              className={`${styles.btnAmem} ${comentar.isPending ? styles.btnComentarioEnviando : ''}`}
              disabled={comentar.isPending || (!novoComentario.trim() && !textoEnviando)}
            >
              {comentar.isPending ? (
                <Loader2 size={12} className={styles.spinnerIcone} />
              ) : (
                <Send size={12} />
              )}
            </button>
          </form>
        </div>
      </Colapsavel>

      {modalEditarAberto && (
        <ModalEditarPostagem
          postagem={postagem}
          aoFechar={() => setModalEditarAberto(false)}
        />
      )}

      {comentarioEditarModal && (
        <ModalEditarComentario
          postagemId={postagem.id}
          comentarioId={comentarioEditarModal.id}
          conteudoInicial={comentarioEditarModal.conteudo}
          aoFechar={() => setComentarioEditarModal(null)}
        />
      )}

      {confirmarExcluirPost && (
        <ModalConfirmacaoExclusao
          titulo="Excluir Postagem"
          mensagem="Tem certeza que deseja excluir esta postagem? Esta ação não poderá ser desfeita."
          carregando={deletarPost.isPending}
          onConfirmar={handleExecutarExclusaoPost}
          onClose={() => setConfirmarExcluirPost(false)}
        />
      )}

      {confirmarExcluirComentarioId && (
        <ModalConfirmacaoExclusao
          titulo="Excluir Comentário"
          mensagem="Tem certeza que deseja remover este comentário?"
          carregando={deletarComentario.isPending}
          onConfirmar={() => handleExecutarExclusaoComentario(confirmarExcluirComentarioId)}
          onClose={() => setConfirmarExcluirComentarioId(null)}
        />
      )}

      {fotoModal && (
        <VisualizadorFoto
          fotoId={fotoModal.id}
          descricao={fotoModal.descricao}
          onClose={() => setFotoModal(null)}
        />
      )}

      {modalComentariosMobile && (
        <ModalComentariosMobile
          postagem={postagem}
          aoFechar={() => setModalComentariosMobile(false)}
        />
      )}

      {perfilResumo && (
        <ModalPerfilResumo
          dados={perfilResumo}
          posicaoTarget={posicaoTarget}
          aoFechar={() => {
            setPerfilResumo(null)
            setPosicaoTarget(null)
          }}
          onVerDetalhesCompletos={(id) => setPessoaDetalheId(id)}
        />
      )}

      {pessoaDetalheId && (
        <DrawerDetalhePessoa
          pessoaId={pessoaDetalheId}
          onClose={() => setPessoaDetalheId(null)}
        />
      )}
    </article>
  )
}
