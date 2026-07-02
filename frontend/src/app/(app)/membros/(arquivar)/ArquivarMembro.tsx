'use client'
import { Archive, X, Info } from 'lucide-react'
import { useArquivarMembro } from '@/hooks/membro/useArquivarMembro'
import { MembroResponse } from '@/types/membro.type'
import styles from '../../usuarios/(arquivarusuario)/ModalArquivarUsuario.module.css'

export function ModalArquivarMembro({ membro, onClose }: { membro: MembroResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarMembro(membro, onClose)
  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        <div className={styles.topo}>
          <div className={styles.iconBox}><Archive size={28} /></div>
          <h2 className={styles.title}>Arquivar membro?</h2>
          <span className={styles.badge}><span className={styles.badgeDot} />Ação reversível</span>
        </div>
        <p className={styles.text}>
          Ao arquivar <strong>{membro.nome}</strong>, ele deixará de aparecer na lista de membros. Seus dados e histórico serão preservados e poderão ser restaurados por um administrador a qualquer momento.
        </p>
        <div className={styles.infoBox}>
          <Info size={18} className={styles.infoIcon} />
          <p className={styles.infoText}>
            Se este membro tiver acesso ao sistema, o login também será arquivado e ele não poderá mais entrar.
          </p>
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