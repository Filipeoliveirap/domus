import { api } from '@/lib/api'

export interface AdminLoginRequest {
  email: string
  senha: string
}

export interface AdminLoginResponse {
  id: string
  nome: string
  email: string
  token: string
}

export const adminAuthService = {
  async login(payload: AdminLoginRequest): Promise<AdminLoginResponse> {
    const { data } = await api.post<AdminLoginResponse>('/api/admin/auth/login', payload)
    return data
  }
}
