'use client'

import { useEffect, useState } from 'react'
import { Copy, Check, RefreshCw } from 'lucide-react'
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
interface Props {
  qrCode: string
  qrCodeBase64: string
  expiraEm: string
  /** "Gerar novo QR code" / "Pagar com outro método" — achado ao vivo (2026-08-27): sem
   *  isto, um QR que não funciona (escaneado errado, banco travou, ou a pessoa quer trocar
   *  pra cartão) prendia a pessoa nesta tela até a cobrança expirar sozinha (até 30min),
   *  sem opção nenhuma. Omitido = tela sem essa opção (ex.: onde ainda não foi ligada). */
  onReiniciar?: () => void
  reiniciando?: boolean
}

export function TelaPix({ qrCode, qrCodeBase64, expiraEm, onReiniciar, reiniciando }: Props) {
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
      <p className={styles.pixAviso}>
        Cuidado ao pagar: escolha a opção <strong>&quot;pagamento&quot;/&quot;Pix Copia e
        Cola&quot;</strong> no seu banco, nunca <strong>&quot;transferência&quot;</strong>. Só
        no modo pagamento a confirmação é automática aqui — uma transferência não é
        reconhecida.
      </p>
      <p className={`${styles.pixContagem} ${restante <= 60 ? styles.pixContagemUrgente : ''}`}>
        {restante > 0 ? <>Você tem <strong>{formatarContagem(restante)}</strong> para pagar</> : 'O tempo para pagar acabou'}
      </p>
      <p className={styles.pixAguardando}>Aguardando confirmação do pagamento…</p>
      {onReiniciar && (
        <button type="button" className={styles.pixReiniciar} onClick={onReiniciar} disabled={reiniciando}>
          <RefreshCw size={14} aria-hidden="true" />
          {reiniciando ? 'Preparando…' : 'QR Code não funcionou? Gerar novo ou pagar de outro jeito'}
        </button>
      )}
    </div>
  )
}
