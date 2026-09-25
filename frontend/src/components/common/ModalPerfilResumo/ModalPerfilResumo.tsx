'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import { X, Building2, Shield, User, MapPin, Mail, Phone, ExternalLink } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { usePessoa } from '@/hooks/pessoa/usePessoa'
import { VisualizadorFoto } from '@/components/common/VisualizadorFoto/VisualizadorFoto'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'
import styles from './ModalPerfilResumo.module.css'

export interface PosicaoTarget {
  top: number
  left: number
  bottom?: number
  right?: number
  height?: number
  width?: number
}

export interface PerfilResumoDados {
  id?: string
  nome: string
  fotoId?: string | null
  cargo?: string | null
  igreja?: {
    id?: string
    nome: string
    sigla?: string | null
  } | null
}

interface Props {
  dados: PerfilResumoDados
  posicaoTarget?: PosicaoTarget | null
  aoFechar: () => void
  onVerDetalhesCompletos?: (id: string) => void
}

export function ModalPerfilResumo({ dados, posicaoTarget, aoFechar, onVerDetalhesCompletos }: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const { data: pessoaInfo } = usePessoa(dados.id)
  const containerRef = useRef<HTMLDivElement>(null)
  const [fotoVisualizando, setFotoVisualizando] = useState(false)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !fotoVisualizando) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, fotoVisualizando])

  useEffect(() => {
    if (window.innerWidth < 768) {
      const anterior = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      return () => {
        document.body.style.overflow = anterior
      }
    }
  }, [])

  const fotoIdParaUsar = dados.fotoId ?? pessoaInfo?.fotoId
  const urlAvatar = urlFoto(fotoIdParaUsar, 'DISPLAY')
  const nomeCompleto = pessoaInfo?.nome ?? dados.nome
  const cargoTexto = dados.cargo ?? pessoaInfo?.vinculo ?? 'Membro'
  const nomeIgreja = dados.igreja?.nome ?? (pessoaInfo?.igrejaId ? 'Congregação Local' : null)
  const siglaIgreja = dados.igreja?.sigla

  const estiloPopover = useMemo<React.CSSProperties>(() => {
    if (typeof window === 'undefined' || window.innerWidth < 768 || !posicaoTarget) {
      return {}
    }
    const LARGURA = 320
    const ALTURA_ESTIMADA = 340

    let top = (posicaoTarget.bottom ?? (posicaoTarget.top + (posicaoTarget.height ?? 0))) + 8
    let left = posicaoTarget.left

    if (top + ALTURA_ESTIMADA > window.innerHeight - 16) {
      top = Math.max(16, posicaoTarget.top - ALTURA_ESTIMADA - 8)
    }

    if (left + LARGURA > window.innerWidth - 16) {
      left = window.innerWidth - LARGURA - 16
    }
    if (left < 16) left = 16

    return {
      position: 'fixed',
      top: `${top}px`,
      left: `${left}px`,
      margin: 0,
      zIndex: 120,
    }
  }, [posicaoTarget])

  return (
    <>
      <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
        <div
          ref={containerRef}
          className={styles.popoverModal}
          style={estiloPopover}
          onMouseDown={(e) => e.stopPropagation()}
          role="dialog"
          aria-modal="true"
          aria-label={`Resumo do perfil de ${nomeCompleto}`}
        >
          <span className={styles.grabber} aria-hidden="true" />

          <button type="button" className={styles.btnFechar} onClick={fechar} aria-label="Fechar">
            <X size={16} />
          </button>

          <div className={styles.bannerBackground} />

          <div className={styles.corpo}>
            <div
              className={`${styles.avatarWrap} ${fotoIdParaUsar ? styles.avatarWrapClicavel : ''}`}
              onClick={() => {
                if (fotoIdParaUsar) setFotoVisualizando(true)
              }}
              title={fotoIdParaUsar ? 'Clique para ampliar a foto' : undefined}
            >
              {urlAvatar ? (
                <Image
                  src={urlAvatar}
                  alt=""
                  width={72}
                  height={72}
                  unoptimized
                  className={styles.avatarFoto}
                />
              ) : (
                <span className={styles.avatarIniciais}>{iniciais(nomeCompleto)}</span>
              )}
            </div>

            <div className={styles.infoPrincipal}>
              <h3 className={styles.nome}>{nomeCompleto}</h3>
              {cargoTexto && (
                <span className={styles.badgeCargo}>
                  <Shield size={12} aria-hidden="true" />
                  {cargoTexto}
                </span>
              )}
            </div>

            {nomeIgreja && (
              <div className={styles.blocoIgreja}>
                <Building2 size={16} className={styles.iconeIgreja} aria-hidden="true" />
                <div className={styles.textosIgreja}>
                  <span className={styles.rotuloIgreja}>Congregação</span>
                  <span className={styles.nomeIgreja}>
                    {nomeIgreja}
                    {siglaIgreja ? ` (${siglaIgreja})` : ''}
                  </span>
                </div>
              </div>
            )}

            {(pessoaInfo?.bairro || pessoaInfo?.email || pessoaInfo?.telefone) && (
              <div className={styles.detalhesAdicionais}>
                {pessoaInfo?.bairro && (
                  <div className={styles.itemDetalhe}>
                    <MapPin size={13} aria-hidden="true" />
                    <span>{pessoaInfo.bairro}</span>
                  </div>
                )}
                {pessoaInfo?.email && (
                  <div className={styles.itemDetalhe}>
                    <Mail size={13} aria-hidden="true" />
                    <span>{pessoaInfo.email}</span>
                  </div>
                )}
                {pessoaInfo?.telefone && (
                  <div className={styles.itemDetalhe}>
                    <Phone size={13} aria-hidden="true" />
                    <span>{pessoaInfo.telefone}</span>
                  </div>
                )}
              </div>
            )}

            {dados.id && onVerDetalhesCompletos && (
              <button
                type="button"
                className={styles.btnVerPerfil}
                onClick={() => {
                  fechar()
                  onVerDetalhesCompletos(dados.id!)
                }}
              >
                <User size={14} aria-hidden="true" />
                Ver perfil completo
                <ExternalLink size={12} aria-hidden="true" />
              </button>
            )}
          </div>
        </div>
      </div>

      {fotoVisualizando && fotoIdParaUsar && (
        <VisualizadorFoto
          fotoId={fotoIdParaUsar}
          descricao={`Foto de ${nomeCompleto}`}
          onClose={() => setFotoVisualizando(false)}
        />
      )}
    </>
  )
}
