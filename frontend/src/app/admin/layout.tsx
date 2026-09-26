'use client'

import React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { AdminAuthProvider, useAdminAuth } from '@/contexts/AdminAuthContext'
import styles from './admin.module.css'

function AdminLayoutInner({ children }: { children: React.ReactNode }) {
  const { adminUser, logout, adminToken, isLoading } = useAdminAuth()
  const pathname = usePathname()

  if (pathname === '/admin/login') {
    return <>{children}</>
  }

  if (isLoading) {
    return (
      <div className={styles.paginaAdmin} style={{ alignItems: 'center', justifyContent: 'center' }}>
        <p className="animate-pulse">Carregando painel admin...</p>
      </div>
    )
  }

  if (!adminToken) {
    if (typeof window !== 'undefined') {
      window.location.href = '/admin/login'
    }
    return null
  }

  return (
    <div className={styles.paginaAdmin}>
      <header className={styles.headerAdmin}>
        <div className={styles.logoBox}>
          <Link href="/admin/dashboard" className={styles.logoTitle}>
            DOMUS <span className={styles.badgeTag}>Admin v1</span>
          </Link>
          <nav className={styles.navAdmin}>
            <Link
              href="/admin/dashboard"
              className={`${styles.navLink} ${pathname === '/admin/dashboard' ? styles.navLinkAtivo : ''}`}
            >
              Dashboard
            </Link>
            <Link
              href="/admin/tenants"
              className={`${styles.navLink} ${pathname.startsWith('/admin/tenants') ? styles.navLinkAtivo : ''}`}
            >
              Tenants (Igrejas)
            </Link>
            <Link
              href="/admin/configuracoes"
              className={`${styles.navLink} ${pathname.startsWith('/admin/configuracoes') ? styles.navLinkAtivo : ''}`}
            >
              Configuração MP
            </Link>
          </nav>
        </div>

        <div className={styles.userBox}>
          <span>{adminUser?.email}</span>
          <button onClick={logout} className={styles.btnSair}>
            Sair
          </button>
        </div>
      </header>

      <main className={styles.mainContent}>{children}</main>
    </div>
  )
}

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <AdminAuthProvider>
      <AdminLayoutInner>{children}</AdminLayoutInner>
    </AdminAuthProvider>
  )
}
