import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  listarContas,
  obterConta,
  criarConta,
  editarConta,
  excluirConta,
  registrarPagamento,
  darBaixaRestante,
  obterResumo,
  projetarSerie,
  uploadAnexo,
  deletarAnexo,
} from '@/lib/api/contas-a-pagar'
import type {
  ContaRequest,
  EscopoEdicaoSerie,
  ListarContasFiltros,
  RegistrarPagamentoRequest,
} from '@/types/contas-a-pagar'

export const contasQueryKeys = {
  all: ['contas-a-pagar'] as const,
  list: (filtros: ListarContasFiltros) => [...contasQueryKeys.all, 'listar', filtros] as const,
  detail: (id: string) => [...contasQueryKeys.all, 'detail', id] as const,
  resumo: (mes: string) => [...contasQueryKeys.all, 'resumo', mes] as const,
  projecao: (serieId: string) => [...contasQueryKeys.all, 'projecao', serieId] as const,
}

export function useContasAPagar(
  args: {
    competencia: string
    page: number
    size: number
    filtros: Partial<ListarContasFiltros>
  },
  options?: { queryKey?: Readonly<[string, ...unknown[]]> },
) {
  const params = new URLSearchParams()
  params.set('competencia', args.competencia)
  params.set('page', String(args.page))
  params.set('size', String(args.size))
  if (args.filtros.status) params.set('status', args.filtros.status)
  if (args.filtros.beneficiario) params.set('beneficiario', args.filtros.beneficiario)
  if (args.filtros.vencimentoAte) params.set('vencimentoAte', args.filtros.vencimentoAte)

  const queryKey = options?.queryKey ?? contasQueryKeys.list(args.filtros)

  return useQuery({
    queryKey,
    queryFn: () => listarContas(params),
    placeholderData: keepPreviousData,
  })
}

export function useConta(id: string | null) {
  return useQuery({
    queryKey: contasQueryKeys.detail(id ?? ''),
    queryFn: () => obterConta(id!),
    enabled: !!id,
  })
}

export function useResumo(mesReferencia: string) {
  return useQuery({
    queryKey: contasQueryKeys.resumo(mesReferencia),
    queryFn: () => obterResumo(mesReferencia),
    staleTime: 60_000,
  })
}

export function useProjecaoSerie(serieId: string | null) {
  return useQuery({
    queryKey: contasQueryKeys.projecao(serieId ?? ''),
    queryFn: () => projetarSerie(serieId!),
    enabled: !!serieId,
  })
}

// ─── Mutations ────────────────────────────────────────────────────────────────

export function useCriarConta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: ContaRequest) => criarConta(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: contasQueryKeys.all })
    },
  })
}

export function useEditarConta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, req, escopo }: { id: string; req: ContaRequest; escopo: EscopoEdicaoSerie }) =>
      editarConta(id, req, escopo),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: contasQueryKeys.all })
    },
  })
}

export function useExcluirConta() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, escopo }: { id: string; escopo: EscopoEdicaoSerie }) =>
      excluirConta(id, escopo),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: contasQueryKeys.all })
    },
  })
}

export function useRegistrarPagamento() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ contaId, req }: { contaId: string; req: RegistrarPagamentoRequest }) =>
      registrarPagamento(contaId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: contasQueryKeys.all })
    },
  })
}

export function useDarBaixaRestante() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (contaId: string) => darBaixaRestante(contaId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: contasQueryKeys.all })
    },
  })
}

export function useUploadAnexo() {
  return useMutation({
    mutationFn: (file: File) => uploadAnexo(file),
  })
}

export function useDeletarAnexo() {
  return useMutation({
    mutationFn: (id: string) => deletarAnexo(id),
  })
}
