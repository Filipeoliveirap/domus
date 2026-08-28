'use client'

import Link, { useLinkStatus } from 'next/link'
import Image from 'next/image'
import { usePathname, useRouter } from 'next/navigation'
import { useState } from 'react'
import {
  Home, LayoutDashboard, Users, Calendar, Wallet, UserCog, Settings, User, LogOut, ChevronDown, UsersRound, Grid3x3,
} from 'lucide-react'
import { queryClient } from '@/lib/queryClient'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { authService } from '@/services/auth.service'
import type { Role } from '@/types/usuario.types'
import { urlFoto } from '@/lib/urlFoto'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { podeGerenciarVisitantes, podeVerFinanceiro } from '@/lib/permissoes'
import { MODO_INDICADOR_NAV } from '@/config/navIndicator'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './Sidebar.module.css'

type NavItem = { href: string; label: string; icon: typeof Home; roles: Role[]; visivel?: (role: Role | null, caps: string[]) => boolean }

const pessoasSubItems: { href: string; label: string; roles: Role[]; visivel?: (r: Role | null, c: string[]) => boolean }[] = [
  { href: '/pessoas', label: 'Pessoas', roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
  { href: '/pessoas/visitantes', label: 'Visitantes', roles: ['ADMIN_IGREJA'], visivel: (r, c) => podeGerenciarVisitantes(r, c) },
]

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

/** Renderiza o ícone do item; no modo 'barra-e-link', troca por um spinner enquanto a
 *  navegação disparada por este link está pendente. `useLinkStatus` só funciona como
 *  descendente de um <Link> do next. */
function IconePendente({ icon: Icon }: { icon: typeof Home }) {
  const { pending } = useLinkStatus()
  if (MODO_INDICADOR_NAV === 'barra-e-link' && pending) {
    return <Loader variant="circular" size="sm" />
  }
  return <Icon size={20} />
}

export function Sidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { ministerio, celula } = useRotulos()
  const role = useAuthStore((state) => state.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const nome = useAuthStore((state) => state.nome)
  const fotoId = useAuthStore((state) => state.fotoId)
  const cargo = useAuthStore((state) => state.cargo)
  const logout = useAuthStore((state) => state.logout)
  const navAberta = useUiStore((state) => state.navAberta)
  const fecharNav = useUiStore((state) => state.fecharNav)

  const navItems: NavItem[] = [
    { href: '/inicio',     label: 'Início',    icon: Home,            roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/dashboard',  label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN_IGREJA'] },
    { href: '/eventos',    label: 'Eventos',   icon: Calendar,        roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/ministerios', label: ministerio.plural, icon: UsersRound, roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/celulas',    label: celula.plural,  icon: Grid3x3,          roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/financeiro/movimentacoes', label: 'Financeiro',  icon: Wallet,          roles: ['ADMIN_IGREJA'], visivel: (r, c) => podeVerFinanceiro(r, c) },
    { href: '/usuarios',   label: 'Usuários',  icon: UserCog,         roles: ['ADMIN_IGREJA'] },
  ]

  const filtrar = <T extends { roles: Role[]; visivel?: (r: Role | null, c: string[]) => boolean }>(items: T[]) =>
    items.filter((item) => {
      if (item.visivel) return item.visivel(role, capacidadesExtras)
      return role ? item.roles.includes(role) : false
    })

  const renderLink = (item: { href: string; label: string; icon: typeof Home }) => {
    const ativo = pathname === item.href

    return (
      <Link
        key={item.href}
        href={item.href}
        onClick={fecharNav}
        className={ativo ? `${styles.link} ${styles.linkActive}` : `${styles.link} ${styles.linkInactive}`}
      >
        <IconePendente icon={item.icon} />
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
    queryClient.clear()
    router.replace('/login')
  }

  const subItensVisiveis = filtrar(configuracoesSubItems)
  const pessoasSubItensVisiveis = filtrar(pessoasSubItems)
  const [configAberto, setConfigAberto] = useState(
    () => pathname.startsWith('/configuracoes') || pathname === '/perfil',
  )
  const [pessoasAberto, setPessoasAberto] = useState(
    () => pathname.startsWith('/pessoas'),
  )

  return (
    <>
    <div
      className={`${styles.overlay} ${navAberta ? styles.overlayVisivel : ''}`}
      onClick={fecharNav}
      aria-hidden="true"
    />
    <aside className={`${styles.sidebar} ${navAberta ? styles.sidebarAberta : ''}`}>
      <Link href="/inicio" className={styles.header}>
        <Image src="/images/logo2.png" alt="Domus" width={28} height={47} className={styles.logo} />
        <span>
          <h1 className={styles.title}>DOMUS</h1>
          <p className={styles.subtitle}>Gestão Eclesiástica</p>
        </span>
      </Link>

      <nav className={styles.nav}>
        {filtrar(navItems).map(renderLink)}
        {pessoasSubItensVisiveis.length > 0 && (
          <div className={styles.grupo}>
            <button
              type="button"
              onClick={() => setPessoasAberto((v) => !v)}
              aria-expanded={pessoasAberto}
              className={`${styles.link} ${styles.grupoBotao} ${
                pathname.startsWith('/pessoas')
                  ? styles.linkActive
                  : styles.linkInactive
              }`}
            >
              <span className={styles.grupoBotaoConteudo}>
                <Users size={20} />
                <span className={styles.label}>Pessoas</span>
              </span>
              <ChevronDown
                size={16}
                className={`${styles.seta} ${pessoasAberto ? styles.setaAberta : ''}`}
                aria-hidden="true"
              />
            </button>

            <div className={`${styles.submenuWrap} ${pessoasAberto ? styles.submenuAberto : ''}`}>
              <div className={styles.submenu}>
                {pessoasSubItensVisiveis.map((sub) => (
                  <Link
                    key={sub.href}
                    href={sub.href}
                    onClick={fecharNav}
                    tabIndex={pessoasAberto ? 0 : -1}
                    aria-hidden={!pessoasAberto}
                    className={`${styles.subLink} ${
                      pathname === sub.href || (sub.href !== '/pessoas' && pathname.startsWith(sub.href))
                        ? styles.subLinkAtivo
                        : ''
                    }`}
                  >
                    {sub.label}
                  </Link>
                ))}
              </div>
            </div>
          </div>
        )}
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

            <div className={`${styles.submenuWrap} ${configAberto ? styles.submenuAberto : ''}`}>
              <div className={styles.submenu}>
                {subItensVisiveis.map((sub) => (
                  <Link
                    key={sub.href}
                    href={sub.href}
                    onClick={fecharNav}
                    tabIndex={configAberto ? 0 : -1}
                    aria-hidden={!configAberto}
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
            </div>
          </div>
        )}
        <button type="button" onClick={handleLogout} className={`${styles.link} ${styles.logout}`}>
          <LogOut size={20} />
          <span className={styles.label}>Sair</span>
        </button>
      </div>

      <Link href="/perfil" className={styles.profile} onClick={fecharNav}>
        {urlFoto(fotoId, 'THUMB') ? (
          <Image src={urlFoto(fotoId, 'THUMB')!} alt={nome ?? 'Perfil'} width={40} height={40} unoptimized className={styles.profileAvatar} />
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

function primeirosDoisNomes(nome: string | null): string {
  if (!nome) return 'Usuário'
  const partes = nome.trim().split(/\s+/)
  if (partes.length <= 2) return nome
  return partes.slice(0, 2).join(' ')
}
