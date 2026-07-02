'use client'
import { Archive, X, Info } from 'lucide-react'
import { useArquivarUsuario } from '@/hooks/usuario/useArquivarUsuario'
import { UsuarioResponse } from '@/types/usuario.types'
import styles from './ModalArquivarUsuario.module.css'

export function ModalArquivarUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarUsuario(usuario, onClose)

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>

        <div className={styles.topo}>
          <div className={styles.iconBox}><Archive size={28} /></div>
          <h2 className={styles.title}>Arquivar usuário?</h2>
          <span className={styles.badge}><span className={styles.badgeDot} />Ação reversível</span>
        </div>

        <p className={styles.text}>
          Ao arquivar <strong>{usuario.nome}</strong>, ele deixará de aparecer na lista ativa e perderá o acesso ao sistema. Seus dados e histórico serão preservados e movidos para a aba de  &apos;Arquivados&apos;, onde poderão ser restaurados por um administrador a qualquer momento.
        </p>

        <div className={styles.infoBox}>
          <Info size={18} className={styles.infoIcon} />
          <p className={styles.infoText}>O arquivamento não exclui as contribuições financeiras ou registros históricos vinculados a este perfil.</p>
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