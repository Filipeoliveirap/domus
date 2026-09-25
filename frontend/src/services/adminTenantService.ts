import { api } from '@/lib/api'

export interface AdminDashboardDTO {
  mrrEstimado: number
  totalIgrejas: number
  igrejasAtivas: number
  igrejasSuspensas: number
  igrejasTrial: number
  totalPessoasAtivas: number
  alertasLimite: Array<{
    igrejaId: string
    nome: string
    plano: string
    limitePessoas: number
    pessoasAtivas: number
    percentualUso: number
  }>
}

export interface AdminTenantDTO {
  id: string
  nome: string
  cnpj?: string
  emailContato: string
  telefoneContato?: string
  plano: 'BASICO' | 'PRO' | 'PRO_PLUS' | 'ENTERPRISE'
  statusAssinatura: 'TRIAL' | 'ATIVA' | 'PAUSADA' | 'CANCELADA'
  statusTenant: 'ATIVO' | 'SUSPENSO'
  motivoSuspensao?: string
  createdAt: string
  totalPessoas: number
  totalUsuarios: number
}

export interface ConfiguracaoMercadoPagoDTO {
  accessToken: string
  publicKey: string
  configurado: boolean
}

export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  size: number
  number: number
}

export const adminTenantService = {
  async obterDashboardMetrics(adminToken: string): Promise<AdminDashboardDTO> {
    const { data } = await api.get<AdminDashboardDTO>('/api/admin/dashboard', {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async listarTenants(adminToken: string, busca?: string, page = 0, size = 20): Promise<PageResponse<AdminTenantDTO>> {
    const { data } = await api.get<PageResponse<AdminTenantDTO>>('/api/admin/tenants', {
      params: { busca, page, size },
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async buscarPorId(adminToken: string, id: string): Promise<AdminTenantDTO> {
    const { data } = await api.get<AdminTenantDTO>(`/api/admin/tenants/${id}`, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async alterarStatus(adminToken: string, id: string, payload: { statusTenant: 'ATIVO' | 'SUSPENSO'; motivoSuspensao?: string }): Promise<AdminTenantDTO> {
    const { data } = await api.patch<AdminTenantDTO>(`/api/admin/tenants/${id}/status`, payload, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async alterarPlano(adminToken: string, id: string, payload: { plano: string; statusAssinatura: string }): Promise<AdminTenantDTO> {
    const { data } = await api.patch<AdminTenantDTO>(`/api/admin/tenants/${id}/plano`, payload, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async impersonarTenant(adminToken: string, id: string): Promise<{ token: string; igrejaId: string; igrejaNome: string }> {
    const { data } = await api.post<{ token: string; igrejaId: string; igrejaNome: string }>(`/api/admin/tenants/${id}/impersonate`, {}, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async obterConfiguracaoMercadoPago(adminToken: string): Promise<ConfiguracaoMercadoPagoDTO> {
    const { data } = await api.get<ConfiguracaoMercadoPagoDTO>('/api/admin/configuracoes/mercadopago', {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  },

  async salvarConfiguracaoMercadoPago(adminToken: string, payload: { accessToken?: string; publicKey?: string }): Promise<ConfiguracaoMercadoPagoDTO> {
    const { data } = await api.put<ConfiguracaoMercadoPagoDTO>('/api/admin/configuracoes/mercadopago', payload, {
      headers: { Authorization: `Bearer ${adminToken}` }
    })
    return data
  }
}
