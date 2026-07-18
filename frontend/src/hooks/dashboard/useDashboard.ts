import { useQuery } from '@tanstack/react-query'
import { dashboardService } from '@/services/dashboard.service'

// enabled=false para não-admin: evita chamar o endpoint (que responderia 403).
export function useDashboard(enabled = true) {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: () => dashboardService.carregar(),
    enabled,
    staleTime: 2 * 60 * 1000,
  })
}
