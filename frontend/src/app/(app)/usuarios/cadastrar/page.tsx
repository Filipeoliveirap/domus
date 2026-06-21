'use client'

import { useState } from 'react'
import Link from 'next/link'
import {
  UserPlus, Eye, EyeOff, ArrowRight, ShieldCheck,
  Users as UsersIcon, User, CheckCircle2, ChevronRight,
} from 'lucide-react'
import { useRegistrarUsuario } from '@/hooks/usuario/useRegistrarUsuario'
import styles from './Cadastrar.module.css'

const roleOptions = [
  { value: 'ADMIN_IGREJA', label: 'ADMIN_IGREJA', badge: 'Gestor Total', icon: ShieldCheck,
    descricao: 'Acesso total ao sistema, configurações da igreja, gestão financeira e de membros.' },
  { value: 'LIDER', label: 'LIDER', badge: 'Gestor de Grupo', icon: UsersIcon,
    descricao: 'Gestão de membros e eventos de seus ministérios, sem acesso ao financeiro global.' },
  { value: 'MEMBRO', label: 'MEMBRO', badge: 'Restrito', icon: User,
    descricao: 'Acesso apenas ao perfil pessoal, calendário de eventos e contribuições próprias.' },
] as const

export default function CadastrarUsuarioPage() {
  const {
    register, handleSubmit, errors,
    isFormIncomplete, erroGeral, isLoading, onSubmit,
  } = useRegistrarUsuario()

  const [verSenha, setVerSenha] = useState(false)

  return (
    <div className={styles.container}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} />
        <Link href="/usuarios" className={styles.breadcrumbLink}>Usuários</Link>
        <ChevronRight size={16} />
        <span className={styles.breadcrumbCurrent}>Novo cadastro</span>
      </div>

      {/* Card do formulário */}
      <div className={styles.card}>
        <div className={styles.cardHeader}>
          <div>
            <h1 className={styles.cardTitle}>Cadastrar novo usuário</h1>
            <p className={styles.cardSubtitle}>Preencha os dados abaixo para conceder acesso ao sistema.</p>
          </div>
          <div className={styles.headerIcon}>
            <UserPlus size={28} />
          </div>
        </div>

        {erroGeral && <div className={styles.alertError}>{erroGeral}</div>}

        <form onSubmit={handleSubmit(onSubmit)} className={styles.form}>
          {/* nome + email */}
          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="nomeUsuario" className={styles.label}>Nome do usuário</label>
              <input id="nomeUsuario" type="text" placeholder="Ex: João Silva" {...register('nomeUsuario')}
                className={styles.input} />
              {errors.nomeUsuario && <p className={styles.errorMsg}>{errors.nomeUsuario.message}</p>}
            </div>
            <div className={styles.field}>
              <label htmlFor="emailUsuario" className={styles.label}>E-mail do usuário</label>
              <input id="emailUsuario" type="email" placeholder="joao.silva@exemplo.com" {...register('emailUsuario')}
                className={styles.input} />
              {errors.emailUsuario && <p className={styles.errorMsg}>{errors.emailUsuario.message}</p>}
            </div>
          </div>

          {/* senha + confirmar */}
          <div className={styles.row}>
            <div className={styles.field}>
              <label htmlFor="senhaUsuario" className={styles.label}>Senha do usuário</label>
              <div className={styles.inputWrapper}>
                <input id="senhaUsuario" type={verSenha ? 'text' : 'password'} placeholder="••••••••" {...register('senhaUsuario')}
                  className={styles.input} />
                <button type="button" onClick={() => setVerSenha((v) => !v)} className={styles.toggleSenha}>
                  {verSenha ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.senhaUsuario && <p className={styles.errorMsg}>{errors.senhaUsuario.message}</p>}
            </div>
            <div className={styles.field}>
              <label htmlFor="confirmarSenha" className={styles.label}>Confirmar senha</label>
              <input id="confirmarSenha" type={verSenha ? 'text' : 'password'} placeholder="••••••••" {...register('confirmarSenha')}
                className={styles.input} />
              {errors.confirmarSenha && <p className={styles.errorMsg}>{errors.confirmarSenha.message}</p>}
            </div>
          </div>

          {/* seleção de perfil */}
          <div className={styles.roleSection}>
            <label className={styles.label}>Perfil de acesso</label>
            <div className={styles.roleGrid}>
              {roleOptions.map((opt) => {
                const Icon = opt.icon
                return (
                  <label key={opt.value} className={styles.roleOption}>
                    <input type="radio" value={opt.value} {...register('role')} className={styles.roleInput} />
                    <div className={styles.roleCard}>
                      <div className={styles.roleIcon}>
                        <Icon size={20} />
                      </div>
                      <div>
                        <div className={styles.roleTitleRow}>
                          <span className={styles.roleName}>{opt.label}</span>
                          <span className={styles.roleBadge}>{opt.badge}</span>
                        </div>
                        <p className={styles.roleDesc}>{opt.descricao}</p>
                      </div>
                      <div className={styles.roleCheck}>
                        <CheckCircle2 size={24} />
                      </div>
                    </div>
                  </label>
                )
              })}
            </div>
            {errors.role && <p className={styles.errorMsg}>{errors.role.message}</p>}
          </div>

          {/* botões */}
          <div className={styles.actions}>
            <Link href="/usuarios" className={styles.btnCancel}>Cancelar</Link>
            <button type="submit" disabled={isFormIncomplete || isLoading} className={styles.btnSubmit}>
              <span>{isLoading ? 'Cadastrando...' : 'Cadastrar usuário'}</span>
              {!isLoading && <ArrowRight size={16} />}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}