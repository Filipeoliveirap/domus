'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { X, Clock, Pencil, Trash2, Globe } from 'lucide-react'
import { clsx } from 'clsx'
import { useRotulos } from '@/lib/rotulos/useRotulos'

import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAuthStore } from '@/store/authStore'
import { useDeletarPostagem } from '@/hooks/postagem/useDeletarPostagem'
import { ModalEditarPostagem } from './ModalEditarPostagem'
import { ModalConfirmacaoExclusao } from '@/components/common/ModalConfirmacaoExclusao/ModalConfirmacaoExclusao'
import { ModalPerfilResumo, type PerfilResumoDados, type PosicaoTarget } from '@/components/common/ModalPerfilResumo/ModalPerfilResumo'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { formatarTipoAviso } from './MuralAvisosCarrossel'
import type { Postagem } from '@/types/postagem.type'

import styles from './ModalDetalheAviso.module.css'

interface Props {
  aviso: Postagem
  aoFechar: () => void
  onDeletar?: (avisoId: string) => void
  onEditar?: (avisoId: string) => void
}

export function ModalDetalheAviso({ aviso, aoFechar, onDeletar, onEditar }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const { congregacao, concordar } = useRotulos()
  const deletarPost = useDeletarPostagem()

  const role = useAuthStore((s) => s.role)
  const meuId = useAuthStore((s) => s.id)
  const meuPessoaId = useAuthStore((s) => s.pessoaId) ?? meuId
  const minhaIgrejaId = useAuthStore((s) => s.igrejaId)

  const ehAdminOuLider = role === 'ADMIN_IGREJA' || role === 'LIDER'
  const ehAutor = aviso.autor.id === meuPessoaId || aviso.autor.id === meuId
  const ehOutraIgreja = aviso.igrejaAutor != null && aviso.igrejaAutor.id !== minhaIgrejaId
  const podeGerenciar = aviso.podeEditar ?? (ehAutor || (ehAdminOuLider && !ehOutraIgreja))

  const [fotoAmpliada, setFotoAmpliada] = useState(false)
  const [modalEditarAberto, setModalEditarAberto] = useState(false)
  const [confirmarExcluir, setConfirmarExcluir] = useState(false)
  const [perfilResumo, setPerfilResumo] = useState<PerfilResumoDados | null>(null)
  const [posicaoTarget, setPosicaoTarget] = useState<PosicaoTarget | null>(null)

  const urlAvatar = urlFoto(aviso.autor.fotoId, 'THUMB')
  const urlFotoAviso = urlFoto(aviso.fotoId, 'DISPLAY')

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = anterior
    }
  }, [])

  const handleExcluirAviso = () => {
    setConfirmarExcluir(false)
    fechar()
    if (onDeletar) {
      onDeletar(aviso.id)
    } else {
      setTimeout(() => {
        deletarPost.mutate(aviso.id)
      }, 220)
    }
  }

  return (
    <>
      <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
        <div
          className={styles.modal}
          onMouseDown={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
        >
          <div className={styles.modalHeader}>
            <h2 className={styles.modalTitulo}>Detalhes do Aviso</h2>
            <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
              <X size={18} />
            </button>
          </div>

          <div className={styles.modalCorpo}>
            <div className={styles.cabecalhoMeta}>
              <div
                className={styles.autorBloco}
                onClick={(e) => {
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
                    id: aviso.autor.id,
                    nome: aviso.autor.nome,
                    fotoId: aviso.autor.fotoId,
                    cargo: aviso.autor.cargo,
                    igreja: aviso.igrejaAutor,
                  })
                }}
                role="button"
                tabIndex={0}
                style={{ cursor: 'pointer' }}
              >
                <div className={styles.avatar}>
                  {urlAvatar ? (
                    <Image
                      src={urlAvatar}
                      alt=""
                      width={40}
                      height={40}
                      unoptimized
                      style={{ borderRadius: '50%' }}
                    />
                  ) : (
                    iniciais(aviso.autor.nome)
                  )}
                </div>
                <div className={styles.autorInfo}>
                  <span className={styles.nomeAutor}>{doisPrimeirosNomes(aviso.autor.nome)}</span>
                  <span className={styles.metaPost}>
                    <Clock size={12} style={{ display: 'inline', marginRight: 4 }} />
                    {new Date(aviso.criadoEm).toLocaleDateString('pt-BR')}
                  </span>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                {aviso.restritoPropriaIgreja === false && (
                  <span
                    className={styles.tagRede}
                    title={`Aviso compartilhado com ${concordar(congregacao.genero, 'os_min')} demais ${congregacao.plural.toLowerCase()}`}
                  >
                    <Globe size={11} aria-hidden="true" />
                    {congregacao.plural}
                  </span>
                )}
                <span className={styles.tagPost}>{formatarTipoAviso(aviso.tipo)}</span>
              </div>
            </div>

            {aviso.titulo && <h3 className={styles.tituloAviso}>{aviso.titulo}</h3>}
            {aviso.conteudo && <p className={styles.conteudoAviso}>{aviso.conteudo}</p>}

            {aviso.fotoId && urlFotoAviso && (
              <div className={styles.fotoContainer} onClick={() => setFotoAmpliada(true)}>
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={urlFotoAviso}
                  alt={aviso.titulo ?? 'Foto do aviso'}
                  className={styles.fotoAviso}
                  loading="lazy"
                />
              </div>
            )}

            {aviso.versiculoRef && (
              <div className={styles.blocoVersiculo}>
                &ldquo;{aviso.conteudo.slice(0, 100)}...&rdquo;
                <span className={styles.refVersiculo}>— {aviso.versiculoRef}</span>
              </div>
            )}
          </div>

          <div className={styles.modalRodape}>
            {podeGerenciar && (
              <div style={{ display: 'flex', gap: '8px' }}>
                <button
                  type="button"
                  className={styles.btnAcaoAviso}
                  onClick={() => setModalEditarAberto(true)}
                >
                  <Pencil size={14} />
                  Editar
                </button>
                <button
                  type="button"
                  className={`${styles.btnAcaoAviso} ${styles.btnExcluirAviso}`}
                  onClick={() => setConfirmarExcluir(true)}
                >
                  <Trash2 size={14} />
                  Excluir
                </button>
              </div>
            )}

            <button type="button" className={styles.btnFecharRodape} onClick={fechar}>
              Fechar
            </button>
          </div>
        </div>
      </div>

      {modalEditarAberto && (
        <ModalEditarPostagem
          postagem={aviso}
          aoFechar={() => setModalEditarAberto(false)}
          onEditarSuccess={(id) => {
            setModalEditarAberto(false)
            fechar()
            onEditar?.(id)
          }}
        />
      )}

      {confirmarExcluir && (
        <ModalConfirmacaoExclusao
          titulo="Excluir Aviso"
          mensagem="Tem certeza que deseja excluir este aviso do mural oficial?"
          carregando={deletarPost.isPending}
          onConfirmar={handleExcluirAviso}
          onClose={() => setConfirmarExcluir(false)}
        />
      )}

      {fotoAmpliada && aviso.fotoId && (
        <VisualizadorFoto
          fotoId={aviso.fotoId}
          descricao={aviso.titulo ?? 'Foto do aviso'}
          onClose={() => setFotoAmpliada(false)}
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
        />
      )}
    </>
  )
}
