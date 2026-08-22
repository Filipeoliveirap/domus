'use client'

import { useEffect, useState } from 'react'
import { X, Copy, Check } from 'lucide-react'
import { useGerarConvite } from '@/hooks/inscricao/useGerarConvite'
import painelStyles from './ModalInscreverPessoas.module.css'
import styles from './ModalCompartilharConvite.module.css'

interface Props {
  eventoId: string
  onClose: () => void
}

export function ModalCompartilharConvite({ eventoId, onClose }: Props) {
  const [copiado, setCopiado] = useState(false)

  // Compartilhar não exige estar inscrito — o link funciona pra quem vai usá-lo, não pra
  // quem compartilha (ver useGerarConvite: query habilitada direto, sem pergunta antes).
  const gerarConvite = useGerarConvite(eventoId, true)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  function copiarLink() {
    if (!gerarConvite.data) return
    navigator.clipboard.writeText(gerarConvite.data.link)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  function abrirWhatsapp() {
    if (!gerarConvite.data) return
    const texto = encodeURIComponent(`Você foi convidado! ${gerarConvite.data.link}`)
    window.open(`https://wa.me/?text=${texto}`, '_blank')
  }

  return (
    <div className={painelStyles.overlay} onMouseDown={onClose}>
      <div
        className={painelStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-compartilhar-convite"
      >
        <div className={painelStyles.header}>
          <div>
            <h2 className={painelStyles.titulo} id="titulo-compartilhar-convite">Compartilhar evento</h2>
          </div>
          <button type="button" className={painelStyles.btnFechar} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.corpo}>
          <p className={styles.aviso}>Quem usar este link entra como seu convidado.</p>

          {gerarConvite.isPending && <p className={styles.estado}>Gerando link…</p>}
          {gerarConvite.isError && <p className={styles.estado}>Não foi possível gerar o link. Tente novamente.</p>}

          {gerarConvite.data && (
            <>
              <div className={styles.linkBox}>
                <input type="text" readOnly value={gerarConvite.data.link} className={styles.linkInput} />
                <button type="button" onClick={copiarLink} className={styles.btnCopiar}>
                  {copiado ? <Check size={16} /> : <Copy size={16} />}
                  {copiado ? 'Copiado' : 'Copiar'}
                </button>
              </div>
              <button type="button" onClick={abrirWhatsapp} className={styles.btnWhatsapp}>
                Enviar por WhatsApp
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
