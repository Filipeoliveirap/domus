import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { DashboardResponse } from '@/types/dashboard.type'

export const dashboardService = {
  carregar: (): Promise<DashboardResponse> =>
    api.get<DashboardResponse>(Endpoints.dashboard.GET).then((res) => res.data),
}
