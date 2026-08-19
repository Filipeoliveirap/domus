'use client'

import { useState } from 'react'
import Link from 'next/link'
import { AlertTriangle } from 'lucide-react'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { useAuthStore } from '@/store/authStore'
import styles from './ModalReaceitarTermos.module.css'

/**
 * Modal bloqueante — sem "X", sem clicar fora, sem navegar — até a pessoa aceitar de
 * novo os Termos/Política. Cobre tanto quem tinha aceitado uma versão antiga quanto
 * contas criadas antes desta feature (nunca tiveram nenhum registro).
 */
export function ModalReaceitarTermos() {
  const confirmarAceiteTermos = useAuthStore((s) => s.confirmarAceiteTermos)
  const [aceitou, setAceitou] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function confirmar() {
    setCarregando(true)
    setErro(null)
    try {
      await api.post(Endpoints.termos.ACEITAR)
      confirmarAceiteTermos()
    } catch {
      setErro('Não foi possível registrar seu aceite. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.modal} role="dialog" aria-modal="true">
        <div className={styles.cabecalho}>
          <span className={styles.iconBox}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={styles.titulo}>Atualizamos nossos Termos</h2>
        </div>

        <p className={styles.mensagem}>
          Nossos Termos de Uso e/ou Política de Privacidade mudaram. Pra continuar
          usando o Domus, revise e aceite a versão atual.
        </p>

        <label className={styles.termosLabel}>
          <input
            type="checkbox"
            className={styles.checkbox}
            checked={aceitou}
            onChange={(e) => setAceitou(e.target.checked)}
          />
          <span className={styles.termosTexto}>
            Li e concordo com os{' '}
            <Link href="/termos" className={styles.termosLink} target="_blank">Termos de Uso</Link>
            {' '}e a{' '}
            <Link href="/privacidade" className={styles.termosLink} target="_blank">Política de Privacidade</Link>.
          </span>
        </label>

        {erro && <p className={styles.erro}>{erro}</p>}

        <button
          type="button"
          className={styles.btnConfirmar}
          disabled={!aceitou || carregando}
          onClick={confirmar}
        >
          {carregando ? 'Confirmando…' : 'Aceitar e continuar'}
        </button>
      </div>
    </div>
  )
}
