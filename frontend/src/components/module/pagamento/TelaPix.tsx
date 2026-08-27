'use client'

import { useEffect, useState } from 'react'
import { Copy, Check } from 'lucide-react'
import styles from './TelaPix.module.css'

function segundosRestantes(expiraEm: string): number {
  return Math.max(0, Math.round((new Date(expiraEm).getTime() - Date.now()) / 1000))
}

function formatarContagem(segundos: number): string {
  const minutos = Math.floor(segundos / 60)
  const resto = segundos % 60
  return `${minutos}:${resto.toString().padStart(2, '0')}`
}

/**
 * QR Code + copia-e-cola do Pix — usada tanto na hora de criar o pagamento
 * (`PaymentBrickCheckout`, logo após `onSubmit`) quanto ao retomar a tela depois de um
 * reload (`/eventos/{eventoId}/pagamento/{cobrancaId}`, via `cobrancaService.pix`). Sem
 * poll próprio: quem usa este componente já tem um poll de status rodando por fora.
 *
 * `expiraEm` é só informativo (o poll de status por fora é quem realmente troca de tela
 * quando a cobrança vence) — o contador nunca passa de 00:00, mesmo que o relógio do
 * navegador esteja um pouco atrasado em relação ao servidor.
 */
export function TelaPix({ qrCode, qrCodeBase64, expiraEm }: { qrCode: string; qrCodeBase64: string; expiraEm: string }) {
  const [copiado, setCopiado] = useState(false)
  const [restante, setRestante] = useState(() => segundosRestantes(expiraEm))

  useEffect(() => {
    const intervalo = setInterval(() => setRestante(segundosRestantes(expiraEm)), 1000)
    return () => clearInterval(intervalo)
  }, [expiraEm])

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
      <p className={`${styles.pixContagem} ${restante <= 60 ? styles.pixContagemUrgente : ''}`}>
        {restante > 0 ? <>Você tem <strong>{formatarContagem(restante)}</strong> para pagar</> : 'O tempo para pagar acabou'}
      </p>
      <p className={styles.pixAguardando}>Aguardando confirmação do pagamento…</p>
    </div>
  )
}
