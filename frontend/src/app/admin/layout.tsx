'use client'

import React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { AdminAuthProvider, useAdminAuth } from '@/contexts/AdminAuthContext'

function AdminLayoutInner({ children }: { children: React.ReactNode }) {
  const { adminUser, logout, adminToken, isLoading } = useAdminAuth()
  const pathname = usePathname()

  if (pathname === '/admin/login') {
    return <>{children}</>
  }

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-900 text-white">
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
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      <header className="bg-slate-900 border-b border-slate-800 px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <Link href="/admin/dashboard" className="text-xl font-bold tracking-wider text-indigo-400">
            DOMUS <span className="text-xs bg-indigo-500/20 text-indigo-300 px-2 py-0.5 rounded uppercase font-semibold">Admin v1</span>
          </Link>
          <nav className="flex gap-4 text-sm font-medium">
            <Link
              href="/admin/dashboard"
              className={`px-3 py-1.5 rounded transition ${pathname === '/admin/dashboard' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-800'}`}
            >
              Dashboard
            </Link>
            <Link
              href="/admin/tenants"
              className={`px-3 py-1.5 rounded transition ${pathname.startsWith('/admin/tenants') ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-800'}`}
            >
              Tenants (Igrejas)
            </Link>
            <Link
              href="/admin/configuracoes"
              className={`px-3 py-1.5 rounded transition ${pathname.startsWith('/admin/configuracoes') ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white hover:bg-slate-800'}`}
            >
              Configuração MP
            </Link>
          </nav>
        </div>

        <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-400 font-medium">{adminUser?.email}</span>
          <button
            onClick={logout}
            className="bg-slate-800 hover:bg-red-900/50 hover:text-red-300 text-slate-300 px-3 py-1.5 rounded transition border border-slate-700 hover:border-red-700"
          >
            Sair
          </button>
        </div>
      </header>

      <main className="flex-1 p-6 max-w-7xl w-full mx-auto">{children}</main>
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
