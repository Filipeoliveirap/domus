'use client'
import { ShieldCheck, Users, User, X, Check } from 'lucide-react'
import { usePermissaoUsuario } from '@/hooks/usuario/usePermissaoUsuario'
import { UsuarioResponse, Role } from '@/types/usuario.types'
import styles from './ModalPermissaoUsuario.module.css'

const roleOptions = [
  { value: 'ADMIN_IGREJA', label: 'Administrador', badge: 'Gestor Total', icon: ShieldCheck, descricao: 'Acesso total ao sistema, configurações da igreja, gestão financeira e controle de pessoas.' },
  { value: 'LIDER', label: 'Líder', badge: 'Gestor de Grupo', icon: Users, descricao: 'Acesso às pessoas e gerência de eventos. Sem acesso à gestão financeira global.' },
  { value: 'ACESSO_COMUM', label: 'Acesso comum', badge: 'Restrito', icon: User, descricao: 'Vê pessoas e eventos, e se inscreve. Sem gestão.' },
] as const

export function ModalPermissaoUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { roleSelecionada, setRoleSelecionada, salvar, isLoading, erroGeral, semMudanca } = usePermissaoUsuario(usuario, onClose)

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>Alterar permissões</h2>
            <p className={styles.subtitle}>Editando acesso de: <strong>{usuario.nome}</strong></p>
          </div>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        </div>

        <div className={styles.body}>
          <p className={styles.descricaoTopo}>Selecione o novo nível de privilégio para este usuário. Cada perfil possui permissões específicas.</p>

          <div className={styles.lista}>
            {roleOptions.map((opt) => {
              const Icon = opt.icon
              const selecionada = roleSelecionada === opt.value
              return (
                <button
                  type="button"
                  key={opt.value}
                  onClick={() => setRoleSelecionada(opt.value as Role)}
                  className={`${styles.card} ${selecionada ? styles.cardAtivo : ''}`}
                >
                  <div className={styles.cardIcone}><Icon size={20} /></div>
                  <div className={styles.cardTexto}>
                    <div className={styles.cardTitulo}>
                      <span className={styles.cardNome}>{opt.label}</span>
                      <span className={styles.cardBadge}>{opt.badge}</span>
                    </div>
                    <p className={styles.cardDesc}>{opt.descricao}</p>
                  </div>
                  <span className={`${styles.radio} ${selecionada ? styles.radioAtivo : ''}`}>
                    {selecionada && <Check size={14} />}
                  </span>
                </button>
              )
            })}
          </div>

          {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}
        </div>

        <div className={styles.footer}>
          <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
          <button type="button" disabled={isLoading || semMudanca} className={styles.btnSubmit} onClick={salvar}>
            {isLoading ? 'Salvando...' : 'Salvar alterações'}
          </button>
        </div>
      </div>
    </div>
  )
}