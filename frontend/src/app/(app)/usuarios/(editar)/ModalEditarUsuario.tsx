'use client'
import { ShieldCheck, Users, User, Mail, CheckCircle2, Save, X } from 'lucide-react'
import { useEditarUsuario } from '@/hooks/usuario/useUpdateUsuario'
import { UsuarioResponse } from '@/types/usuario.types'
import styles from './ModalEditarUsuario.module.css'

const roleOptions = [
  { value: 'ADMIN_IGREJA', label: 'ADMIN_IGREJA', icon: ShieldCheck, descricao: 'Acesso total às configurações e gestão da instituição.' },
  { value: 'LIDER', label: 'LIDER', icon: Users, descricao: 'Gestão de grupos, eventos e membros específicos.' },
  { value: 'MEMBRO', label: 'MEMBRO', icon: User, descricao: 'Acesso básico ao portal pessoal e agenda da igreja.' },
] as const

export function ModalEditarUsuario({ usuario, onClose }: { usuario: UsuarioResponse; onClose: () => void }) {
  const { register, handleSubmit, errors, isFormIncomplete, erroGeral, isLoading, onSubmit } = useEditarUsuario(usuario, onClose)

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()}>
        <div className={styles.header}>
          <div>
            <h2 className={styles.title}>Editar usuário</h2>
            <p className={styles.subtitle}>Atualize as informações e o nível de acesso de {usuario.nome}.</p>
          </div>
          <button type="button" className={styles.btnClose} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
        </div>

        {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

        <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="nome" className={styles.label}>Nome completo</label>
            <div className={styles.inputWrapper}>
              <User size={18} className={styles.inputIcon} />
              <input id="nome" type="text" placeholder="Nome do usuário" {...register('nome')} className={styles.input} />
            </div>
            {errors.nome && <p className={styles.errorMsg}>{errors.nome.message}</p>}
          </div>

          <div className={styles.field}>
            <label htmlFor="email" className={styles.label}>Endereço de e-mail</label>
            <div className={styles.inputWrapper}>
              <Mail size={18} className={styles.inputIcon} />
              <input id="email" type="email" placeholder="email@exemplo.com" {...register('email')} className={styles.input} />
            </div>
            {errors.email && <p className={styles.errorMsg}>{errors.email.message}</p>}
          </div>

          <div className={styles.roleSection}>
            <label className={styles.label}>Nível de acesso (perfil)</label>
            <div className={styles.roleGrid}>
              {roleOptions.map((opt) => {
                const Icon = opt.icon
                return (
                  <label key={opt.value} className={styles.roleOption}>
                    <input type="radio" value={opt.value} {...register('role')} className={styles.roleInput} />
                    <div className={styles.roleCard}>
                      <CheckCircle2 size={18} className={styles.roleCheck} />
                      <div className={styles.roleIcon}><Icon size={20} /></div>
                      <h3 className={styles.roleName}>{opt.label}</h3>
                      <p className={styles.roleDesc}>{opt.descricao}</p>
                    </div>
                  </label>
                )
              })}
            </div>
            {errors.role && <p className={styles.errorMsg}>{errors.role.message}</p>}
          </div>

          <div className={styles.footer}>
            <button type="button" className={styles.btnCancel} onClick={onClose}>Cancelar</button>
            <button type="submit" disabled={isFormIncomplete || isLoading} className={styles.btnSubmit}>
              <Save size={16} />
              <span>{isLoading ? 'Salvando...' : 'Salvar alterações'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}