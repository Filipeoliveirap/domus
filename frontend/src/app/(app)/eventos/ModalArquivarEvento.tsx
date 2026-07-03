'use client'

import { Archive, X, Info } from 'lucide-react'
import { useArquivarEvento } from '@/hooks/evento/useArquivarEvento'
import { EventoResponse } from '@/types/evento.type'
import styles from '../../../app/(app)/usuarios/(arquivarusuario)/ModalArquivarUsuario.module.css'

export function ModalArquivarEvento({ evento, onClose }: { evento: EventoResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarEvento(evento, onClose)
  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        <div className={styles.topo}>
          <div className={styles.iconBox}><Archive size={28} /></div>
          <h2 className={styles.title}>Arquivar evento?</h2>
          <span className={styles.badge}><span className={styles.badgeDot} />Ação reversível</span>
        </div>
        <p className={styles.text}>
          Ao arquivar <strong>{evento.titulo}</strong>, ele deixará de aparecer na agenda da igreja.
          Os dados serão preservados e poderão ser restaurados por um administrador a qualquer momento.
        </p>
        <div className={styles.infoBox}>
          <Info size={18} className={styles.infoIcon} />
          <p className={styles.infoText}>O arquivamento não remove registros históricos vinculados a este evento.</p>
        </div>
        {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}
        <div className={styles.footer}>
          <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
          <button type="button" disabled={isLoading} className={styles.btnConfirm} onClick={confirmar}>
            {isLoading ? 'Arquivando...' : 'Confirmar arquivamento'}
          </button>
        </div>
      </div>
    </div>
  )
}