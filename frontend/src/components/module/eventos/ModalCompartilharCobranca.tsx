'use client'

import { useEffect, useState } from 'react'
import { X, Copy, Check } from 'lucide-react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import painelStyles from './ModalInscreverPessoas.module.css'
import styles from './ModalCompartilharConvite.module.css'

interface Props {
  nomePessoa: string
  tituloEvento: string
  valor: number
  /** `CobrancaEvento.tokenLinkPublico` já devolvido na criação da inscrição (Task 9,
   *  `criarParaTerceiro(..., gerarLink = true)`) — este componente não gera nada, só
   *  monta a URL e oferece copiar/WhatsApp. Mesmo padrão de `useGerarConvite`: o link já
   *  existe antes do modal abrir. */
  token: string
  onClose: () => void
}

/**
 * Compartilhar o link de cobrança de UM terceiro (convidado que escolheu "Enviar
 * link" em `EscolhaPagamentoPorPessoa`). Estrutura copiada de `ModalCompartilharConvite`
 * (mesmo overlay/modal de `ModalInscreverPessoas.module.css`, mesma UI de copiar/WhatsApp
 * de `ModalCompartilharConvite.module.css`) — só o texto e a rota mudam.
 */
export function ModalCompartilharCobranca({ nomePessoa, tituloEvento, valor, token, onClose }: Props) {
  const [copiado, setCopiado] = useState(false)

  const link = typeof window !== 'undefined'
    ? `${window.location.origin}/cobranca/${token}`
    : `/cobranca/${token}`

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  function copiarLink() {
    navigator.clipboard.writeText(link)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  function abrirWhatsapp() {
    const texto = encodeURIComponent(
      `Você foi inscrito(a) em ${tituloEvento}! Pague sua parte (${formatarMoeda(valor)}) aqui: ${link}`
    )
    window.open(`https://wa.me/?text=${texto}`, '_blank')
  }

  return (
    <div className={painelStyles.overlay} onMouseDown={onClose}>
      <div
        className={painelStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-compartilhar-cobranca"
      >
        <div className={painelStyles.header}>
          <div>
            <h2 className={painelStyles.titulo} id="titulo-compartilhar-cobranca">
              Link de pagamento de {nomePessoa}
            </h2>
          </div>
          <button type="button" className={painelStyles.btnFechar} onClick={onClose} aria-label="Fechar">
            <X size={20} />
          </button>
        </div>

        <div className={styles.corpo}>
          <p className={styles.aviso}>
            Envie este link para {nomePessoa} pagar a própria inscrição ({formatarMoeda(valor)}).
          </p>

          <div className={styles.linkBox}>
            <input type="text" readOnly value={link} className={styles.linkInput} />
            <button type="button" onClick={copiarLink} className={styles.btnCopiar}>
              {copiado ? <Check size={16} /> : <Copy size={16} />}
              {copiado ? 'Copiado' : 'Copiar'}
            </button>
          </div>
          <button type="button" onClick={abrirWhatsapp} className={styles.btnWhatsapp}>
            Enviar por WhatsApp
          </button>
        </div>
      </div>
    </div>
  )
}
