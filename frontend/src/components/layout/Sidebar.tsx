'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import {
  Home, LayoutDashboard, Users, Calendar, Wallet, UserCog, Settings, User, LogOut,
} from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { authService } from '@/services/auth.service'
import type { Role } from '@/types/usuario.types'
import styles from './Sidebar.module.css'

const navItems: { href: string; label: string; icon: typeof Home; roles: Role[] }[] = [
  { href: '/inicio',     label: 'Início',    icon: Home,            roles: ['ADMIN_IGREJA', 'LIDER', 'MEMBRO'] },
  { href: '/dashboard',  label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN_IGREJA'] },
  { href: '/membros',    label: 'Membros',   icon: Users,           roles: ['ADMIN_IGREJA', 'LIDER', 'MEMBRO'] },
  { href: '/eventos',    label: 'Eventos',   icon: Calendar,        roles: ['ADMIN_IGREJA', 'LIDER', 'MEMBRO'] },
  { href: '/financeiro/movimentacoes', label: 'Finanças',  icon: Wallet,          roles: ['ADMIN_IGREJA'] },
  { href: '/usuarios',   label: 'Usuários',  icon: UserCog,         roles: ['ADMIN_IGREJA'] },
]

const footerItems: { href: string; label: string; icon: typeof Home; roles: Role[] }[] = [
  { href: '/configuracoes', label: 'Configurações', icon: Settings, roles: ['ADMIN_IGREJA', 'LIDER', 'MEMBRO'] },
]

const roleLabels: Record<string, string> = {
  ADMIN_IGREJA: 'Administrador',
  LIDER: 'Líder',
  MEMBRO: 'Membro',
}

const roleStyles: Record<string, string> = {
  ADMIN_IGREJA: styles.roleAdmin,
  LIDER: styles.roleLider,
  MEMBRO: styles.roleMembro,
}

export function Sidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const role = useAuthStore((state) => state.role)
  const nome = useAuthStore((state) => state.nome)
  const foto = useAuthStore((state) => state.foto)
  const logout = useAuthStore((state) => state.logout)
  const navAberta = useUiStore((state) => state.navAberta)
  const fecharNav = useUiStore((state) => state.fecharNav)

  const filtrar = <T extends { roles: Role[] }>(items: T[]) =>
    items.filter((item) => (role ? item.roles.includes(role) : false))

  const renderLink = (item: { href: string; label: string; icon: typeof Home }) => {
    const ativo = pathname === item.href
    const Icon = item.icon

    return (
      <Link
        key={item.href}
        href={item.href}
        onClick={fecharNav}
        className={ativo ? `${styles.link} ${styles.linkActive}` : `${styles.link} ${styles.linkInactive}`}
      >
        <Icon size={20} />
        <span className={styles.label}>{item.label}</span>
      </Link>
    )
  }

  const handleLogout = async () => {
    // Sem argumento: o refresh vai no cookie. E é o servidor quem apaga os cookies —
    // sendo httpOnly, o JS não consegue.
    try {
      await authService.logout()
    } catch {
      // Best-effort: mesmo se a revogação no servidor falhar, limpamos a sessão local.
    }
    logout()
    router.replace('/login')
  }

  const footerLinks = filtrar(footerItems)

  return (
    <>
    <div
      className={`${styles.overlay} ${navAberta ? styles.overlayVisivel : ''}`}
      onClick={fecharNav}
      aria-hidden="true"
    />
    <aside className={`${styles.sidebar} ${navAberta ? styles.sidebarAberta : ''}`}>
      <div className={styles.header}>
        <h1 className={styles.title}>DOMUS</h1>
        <p className={styles.subtitle}>Gestão Eclesiástica</p>
      </div>

      <nav className={styles.nav}>
        {filtrar(navItems).map(renderLink)}
      </nav>

      <div className={styles.footer}>
        {footerLinks.map(renderLink)}
        <button type="button" onClick={handleLogout} className={`${styles.link} ${styles.logout}`}>
          <LogOut size={20} />
          <span className={styles.label}>Sair</span>
        </button>
      </div>

      <Link href="/perfil" className={styles.profile} onClick={fecharNav}>
        {foto ? (
          <img src={foto} alt={nome ?? 'Perfil'} className={styles.profileAvatar} />
        ) : (
          <div className={styles.profileAvatar}>
            <User size={20} />
          </div>
        )}
        <div className={styles.profileInfo}>
          <p className={styles.profileName}>{nome ?? 'Usuário'}</p>
          {role && (
            <span className={`${styles.profileRole} ${roleStyles[role] ?? styles.roleMembro}`}>
              {roleLabels[role] ?? role}
            </span>
          )}
        </div>
      </Link>
    </aside>
    </>
  )
}