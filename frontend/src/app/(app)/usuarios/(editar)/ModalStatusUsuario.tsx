'use client'
import { UserX, UserCheck, X, Info } from 'lucide-react'
import { useStatusUsuario } from '@/hooks/usuario/useStatusUsuario'
import { UsuarioResponse } from '@/types/usuario.types'
import styles from './ModalStatusUsuario.module.css'

export function ModalStatusUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral, novoStatus } = useStatusUsuario(usuario, onClose)
  const desativando = !novoStatus

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div className={`${styles.iconBox} ${desativando ? styles.iconBoxDanger : styles.iconBoxSuccess}`}>
            {desativando ? <UserX size={26} /> : <UserCheck size={26} />}
          </div>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        </div>

        <div className={styles.body}>
          <h2 className={styles.title}>{desativando ? 'Desativar acesso?' : 'Reativar acesso?'}</h2>
          <p className={styles.subtitle}>
            {desativando ? 'Desativando o acesso de: ' : 'Reativando o acesso de: '}
            <strong>{usuario.nome}</strong>
          </p>

          <p className={styles.text}>
            {desativando
              ? 'Ao desativar este usuário, ele perderá o acesso imediato ao sistema. Todos os dados e o histórico são preservados, mas ele não poderá fazer login até que o acesso seja reativado.'
              : 'Ao reativar este usuário, ele volta a ter acesso ao sistema e poderá fazer login normalmente.'}
          </p>

          {desativando && (
            <div className={styles.aviso}>
              <Info size={18} className={styles.avisoIcon} />
              <div>
                <p className={styles.avisoTitulo}>Ação reversível</p>
                <p className={styles.avisoTexto}>Diferente da exclusão, a desativação mantém a integridade dos dados históricos para auditoria.</p>
              </div>
            </div>
          )}

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}
        </div>

        <div className={styles.footer}>
          <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
          <button type="button" disabled={isLoading} className={styles.btnConfirm} onClick={confirmar}>
            {isLoading ? 'Processando...' : desativando ? 'Confirmar desativação' : 'Confirmar reativação'}
          </button>
        </div>
      </div>
    </div>
  )
}