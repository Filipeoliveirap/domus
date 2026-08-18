import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { vinculoService } from '@/services/igreja/vinculo.service'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'

const CHAVE_STATUS = ['igrejas-vinculadas', 'status']

export function useVinculoStatus(enabled = true) {
  return useQuery({
    queryKey: CHAVE_STATUS,
    queryFn: vinculoService.status,
    enabled,
  })
}

export function useConsolidado(periodo: PeriodoRelatorio, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'congregacoes', periodo],
    queryFn: () => vinculoService.consolidado(periodo),
    enabled,
  })
}

function useMutacaoDeVinculo<TVars>(fn: (vars: TVars) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: CHAVE_STATUS })
      // O consolidado muda de tamanho quando uma congregação entra ou sai.
      queryClient.invalidateQueries({ queryKey: ['relatorios'] })
    },
  })
}

export function useGerarCodigo() {
  return useMutacaoDeVinculo<void>(() => vinculoService.gerarCodigo())
}

export function useEntrarNaFamilia() {
  return useMutacaoDeVinculo<string>((codigo) => vinculoService.entrar(codigo))
}

export function useDesvincularCongregacao() {
  return useMutacaoDeVinculo<string>((id) => vinculoService.desvincular(id))
}

export function useSairDaFamilia() {
  return useMutacaoDeVinculo<void>(() => vinculoService.sair())
}
