'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { useState } from 'react'
import {
  Home, LayoutDashboard, Users, Calendar, Wallet, UserCog, Settings, User, LogOut, ChevronDown,
} from 'lucide-react'
import { queryClient } from '@/lib/queryClient'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { authService } from '@/services/auth.service'
import type { Role } from '@/types/usuario.types'
import { urlFoto } from '@/lib/urlFoto'
import styles from './Sidebar.module.css'

const navItems: { href: string; label: string; icon: typeof Home; roles: Role[] }[] = [
  { href: '/inicio',     label: 'Início',    icon: Home,            roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
  { href: '/dashboard',  label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN_IGREJA'] },
  { href: '/pessoas',    label: 'Pessoas',   icon: Users,           roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
  { href: '/eventos',    label: 'Eventos',   icon: Calendar,        roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
  { href: '/financeiro/movimentacoes', label: 'Finanças',  icon: Wallet,          roles: ['ADMIN_IGREJA'] },
  { href: '/usuarios',   label: 'Usuários',  icon: UserCog,         roles: ['ADMIN_IGREJA'] },
]

/**
 * Configurações não é uma tela só — é um grupo de abas. Por isso vira menu expansível:
 * o pai abre/fecha e cada aba tem link próprio (URL, voltar do navegador, link direto).
 *
 * "Meu Perfil" é para todo mundo (é a própria conta). "Dados da Igreja" e "Igrejas
 * Vinculadas" mexem em dado institucional e no vínculo entre igrejas, que expõe
 * financeiro — só ADMIN_IGREJA. O backend trava igual — isto aqui é só a UI acompanhando.
 */
const configuracoesSubItems: { href: string; label: string; roles: Role[] }[] = [
  { href: '/perfil', label: 'Meu Perfil', roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
  { href: '/configuracoes/igreja', label: 'Dados da Igreja', roles: ['ADMIN_IGREJA'] },
  { href: '/configuracoes/igrejas-vinculadas', label: 'Igrejas Vinculadas', roles: ['ADMIN_IGREJA'] },
]

const roleLabels: Record<string, string> = {
  ADMIN_IGREJA: 'Administrador',
  LIDER: 'Líder',
  ACESSO_COMUM: 'Acesso comum',
}

const roleStyles: Record<string, string> = {
  ADMIN_IGREJA: styles.roleAdmin,
  LIDER: styles.roleLider,
  ACESSO_COMUM: styles.roleComum,
}

export function Sidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const role = useAuthStore((state) => state.role)
  const nome = useAuthStore((state) => state.nome)
  const fotoId = useAuthStore((state) => state.fotoId)
  const cargo = useAuthStore((state) => state.cargo)
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
    try {
      await authService.logout()
    } catch {
    }
    logout()
    /*
     * O queryClient é um singleton de módulo e o logout é navegação SPA (sem reload), então
     * sem isto o cache SOBREVIVE à troca de usuário: o próximo admin a logar neste navegador
     * veria, por até 5 minutos (staleTime), os números financeiros da igreja anterior — com
     * cara de dado legítimo, porque as queryKeys não carregam identidade de tenant.
     */
    queryClient.clear()
    router.replace('/login')
  }

  // Todo mundo tem pelo menos "Meu Perfil" — o grupo aparece para todos; o que muda por
  // perfil é QUAIS abas aparecem dentro dele (ver configuracoesSubItems).
  const subItensVisiveis = filtrar(configuracoesSubItems)
  // Começa aberto quando já estamos numa das abas — senão o item ativo ficaria escondido.
  const [configAberto, setConfigAberto] = useState(
    () => pathname.startsWith('/configuracoes') || pathname === '/perfil',
  )

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
        {subItensVisiveis.length > 0 && (
          <div className={styles.grupo}>
            <button
              type="button"
              onClick={() => setConfigAberto((v) => !v)}
              aria-expanded={configAberto}
              className={`${styles.link} ${styles.grupoBotao} ${
                pathname.startsWith('/configuracoes') || pathname === '/perfil'
                  ? styles.linkActive
                  : styles.linkInactive
              }`}
            >
              <span className={styles.grupoBotaoConteudo}>
                <Settings size={20} />
                <span className={styles.label}>Configurações</span>
              </span>
              <ChevronDown
                size={16}
                className={`${styles.seta} ${configAberto ? styles.setaAberta : ''}`}
                aria-hidden="true"
              />
            </button>

            {configAberto && (
              <div className={styles.submenu}>
                {subItensVisiveis.map((sub) => (
                  <Link
                    key={sub.href}
                    href={sub.href}
                    onClick={fecharNav}
                    className={`${styles.subLink} ${
                      pathname === sub.href || (sub.href !== '/perfil' && pathname.startsWith(sub.href))
                        ? styles.subLinkAtivo
                        : ''
                    }`}
                  >
                    {sub.label}
                  </Link>
                ))}
              </div>
            )}
          </div>
        )}
        <button type="button" onClick={handleLogout} className={`${styles.link} ${styles.logout}`}>
          <LogOut size={20} />
          <span className={styles.label}>Sair</span>
        </button>
      </div>

      <Link href="/perfil" className={styles.profile} onClick={fecharNav}>
        {urlFoto(fotoId, 'THUMB') ? (
          // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
          <img src={urlFoto(fotoId, 'THUMB')!} alt={nome ?? 'Perfil'} className={styles.profileAvatar} />
        ) : (
          <div className={styles.profileAvatar}>
            <User size={20} />
          </div>
        )}
        <div className={styles.profileInfo}>
          <p className={styles.profileName}>{primeirosDoisNomes(nome)}</p>
          <span className={`${styles.profileRole} ${role ? roleStyles[role] : styles.roleComum}`}>
            {cargo ?? (role ? roleLabels[role] : '')}
          </span>
        </div>
      </Link>
    </aside>
    </>
  )
}

/** Mostra só os dois primeiros nomes da pessoa para caber na sidebar. */
function primeirosDoisNomes(nome: string | null): string {
  if (!nome) return 'Usuário'
  const partes = nome.trim().split(/\s+/)
  if (partes.length <= 2) return nome
  return partes.slice(0, 2).join(' ')
}