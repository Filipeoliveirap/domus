'use client'

import React, { useState } from 'react'
import { Share2, Copy, Check, MessageCircle, KeyRound } from 'lucide-react'
import styles from './CompartilharConviteCongregacao.module.css'

interface Props {
  codigo?: string | null
  nomeMatriz?: string | null
  onGerar?: () => void
  isGerando?: boolean
}

export function CompartilharConviteCongregacao({ codigo, nomeMatriz, onGerar, isGerando }: Props) {
  const [copiado, setCopiado] = useState(false)

  const temCodigo = Boolean(codigo && codigo.trim() !== '')
  const codFormatado = codigo ? encodeURIComponent(codigo.trim()) : ''

  const urlConvite = typeof window !== 'undefined'
    ? `${window.location.origin}/convite/congregacao?codigo=${codFormatado}`
    : `https://domusigreja.com.br/convite/congregacao?codigo=${codFormatado}`

  const mensagemWhatsapp = encodeURIComponent(
    `Olá! Aqui está o convite para conectar nossa igreja ao Domus com plano já coberto${nomeMatriz ? ` pela ${nomeMatriz}` : ''}:\n${urlConvite}`
  )

  const linkWhatsapp = `https://wa.me/?text=${mensagemWhatsapp}`

  const handleCopiar = async () => {
    if (!temCodigo) return
    try {
      await navigator.clipboard.writeText(urlConvite)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      // Fallback defensivo
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.cabecalho}>
        <Share2 size={16} className={styles.icone} aria-hidden="true" />
        <h4 className={styles.titulo}>Conectar Congregação Filha</h4>
      </div>

      <p className={styles.descricao}>
        Envie este link para os líderes da sua congregação realizarem o cadastro com o plano coberto pela igreja matriz.
      </p>

      {temCodigo ? (
        <>
          <div className={styles.inputGroup}>
            <input
              type="text"
              readOnly
              value={urlConvite}
              className={styles.inputLink}
              aria-label="Link de convite para congregação"
            />
            <button
              type="button"
              onClick={handleCopiar}
              className={styles.btnCopiar}
              aria-label="Copiar link"
            >
              {copiado ? <Check size={14} color="#16a34a" /> : <Copy size={14} />}
              <span>{copiado ? 'Copiado!' : 'Copiar link'}</span>
            </button>
          </div>

          <a
            href={linkWhatsapp}
            target="_blank"
            rel="noopener noreferrer"
            className={styles.btnWhatsapp}
          >
            <MessageCircle size={16} />
            <span>Enviar pelo WhatsApp</span>
          </a>
        </>
      ) : (
        <div>
          <button
            type="button"
            onClick={onGerar}
            disabled={isGerando}
            className={styles.btnGerarLink}
          >
            <KeyRound size={16} aria-hidden="true" />
            <span>{isGerando ? 'Gerando link…' : 'Gerar link de convite'}</span>
          </button>
        </div>
      )}
    </div>
  )
}
