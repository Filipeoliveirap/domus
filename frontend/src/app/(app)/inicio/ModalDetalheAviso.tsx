'use client'

import { useEffect, useState } from 'react'
import Image from 'next/image'
import { X, Clock } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais, doisPrimeirosNomes } from '@/lib/formats/pessoaFormat'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import type { Postagem } from '@/types/postagem.type'
import styles from './ModalDetalheAviso.module.css'

interface Props {
  aviso: Postagem
  aoFechar: () => void
}

export function ModalDetalheAviso({ aviso, aoFechar }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const [fotoAmpliada, setFotoAmpliada] = useState(false)

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
              <div className={styles.autorBloco}>
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

              <span className={styles.tagPost}>{aviso.tipo.replace('_', ' ')}</span>
            </div>

            {aviso.titulo && <h3 className={styles.tituloAviso}>{aviso.titulo}</h3>}

            {aviso.conteudo && <p className={styles.conteudoAviso}>{aviso.conteudo}</p>}

            {aviso.fotoId && urlFotoAviso && (
              <div className={styles.fotoContainer} onClick={() => setFotoAmpliada(true)}>
                <Image
                  src={urlFotoAviso}
                  alt={aviso.titulo ?? 'Foto do aviso'}
                  width={600}
                  height={380}
                  unoptimized
                  className={styles.fotoAviso}
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
            <button type="button" className={styles.btnFecharRodape} onClick={fechar}>
              Fechar
            </button>
          </div>
        </div>
      </div>

      {fotoAmpliada && aviso.fotoId && (
        <VisualizadorFoto
          fotoId={aviso.fotoId}
          descricao={aviso.titulo ?? 'Foto do aviso'}
          onClose={() => setFotoAmpliada(false)}
        />
      )}
    </>
  )
}
