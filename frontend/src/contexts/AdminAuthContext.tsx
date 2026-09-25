'use client'

import React, { createContext, useContext, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { adminAuthService, AdminLoginRequest } from '@/services/adminAuthService'

interface AdminAuthContextType {
  adminToken: string | null
  adminUser: { id: string; nome: string; email: string } | null
  login: (credentials: AdminLoginRequest) => Promise<void>
  logout: () => void
  isLoading: boolean
}

const AdminAuthContext = createContext<AdminAuthContextType | undefined>(undefined)

const TOKEN_KEY = 'domus_admin_token'
const USER_KEY = 'domus_admin_user'

export function AdminAuthProvider({ children }: { children: React.ReactNode }) {
  const [adminToken, setAdminToken] = useState<string | null>(null)
  const [adminUser, setAdminUser] = useState<{ id: string; nome: string; email: string } | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const router = useRouter()

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY)
    const userStr = localStorage.getItem(USER_KEY)
    if (token && userStr) {
      try {
        setAdminToken(token)
        setAdminUser(JSON.parse(userStr))
      } catch {
        localStorage.removeItem(TOKEN_KEY)
        localStorage.removeItem(USER_KEY)
      }
    }
    setIsLoading(false)
  }, [])

  const login = async (credentials: AdminLoginRequest) => {
    const res = await adminAuthService.login(credentials)
    const userObj = { id: res.id, nome: res.nome, email: res.email }

    localStorage.setItem(TOKEN_KEY, res.token)
    localStorage.setItem(USER_KEY, JSON.stringify(userObj))

    setAdminToken(res.token)
    setAdminUser(userObj)
    router.push('/admin/dashboard')
  }

  const logout = () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setAdminToken(null)
    setAdminUser(null)
    router.push('/admin/login')
  }

  return (
    <AdminAuthContext.Provider value={{ adminToken, adminUser, login, logout, isLoading }}>
      {children}
    </AdminAuthContext.Provider>
  )
}

export function useAdminAuth() {
  const context = useContext(AdminAuthContext)
  if (!context) {
    throw new Error('useAdminAuth deve ser usado dentro de um AdminAuthProvider')
  }
  return context
}
