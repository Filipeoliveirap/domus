'use client'

import { useState } from 'react'
import { Copy, Check } from 'lucide-react'
import styles from './TelaPix.module.css'

/**
 * QR Code + copia-e-cola do Pix — usada tanto na hora de criar o pagamento
 * (`PaymentBrickCheckout`, logo após `onSubmit`) quanto ao retomar a tela depois de um
 * reload (`/eventos/{eventoId}/pagamento/{cobrancaId}`, via `cobrancaService.pix`). Sem
 * poll próprio: quem usa este componente já tem um poll de status rodando por fora.
 */
export function TelaPix({ qrCode, qrCodeBase64 }: { qrCode: string; qrCodeBase64: string }) {
  const [copiado, setCopiado] = useState(false)

  function copiarCodigoPix() {
    navigator.clipboard.writeText(qrCode)
    setCopiado(true)
    setTimeout(() => setCopiado(false), 2000)
  }

  return (
    <div className={styles.pix}>
      <p className={styles.pixInstrucao}>Escaneie o QR Code com o app do seu banco:</p>
      {/* eslint-disable-next-line @next/next/no-img-element -- imagem vem em base64 direto da API do Mercado Pago, não é um asset local pro <Image> otimizar */}
      <img
        src={`data:image/png;base64,${qrCodeBase64}`}
        alt="QR Code para pagamento via Pix"
        className={styles.pixQrCode}
      />
      <p className={styles.pixInstrucao}>Ou copie o código Pix (copia e cola):</p>
      <button type="button" className={styles.pixCopiar} onClick={copiarCodigoPix}>
        {copiado ? <Check size={16} /> : <Copy size={16} />}
        {copiado ? 'Copiado!' : 'Copiar código Pix'}
      </button>
      <p className={styles.pixAguardando}>Aguardando confirmação do pagamento…</p>
    </div>
  )
}
