import { Endpoints } from '@/lib/endpoints'
import { api } from '@/lib/api'
import type {
  ContaResponse,
  ContaRequest,
  ContasPagedResponse,
  PagamentoResponse,
  RegistrarPagamentoRequest,
  ResumoResponse,
  ProjecaoItem,
  AnexoUploadResponse,
  EscopoEdicaoSerie,
} from '@/types/contas-a-pagar'

// O módulo financeiro do Domus sempre passa o igreja_id pelo JWT — o back extrai do token.

export async function listarContas(params: URLSearchParams): Promise<ContasPagedResponse> {
  const qs = params.toString()
  return api.get<ContasPagedResponse>(
    `${Endpoints.financeiro.CONTAS_A_PAGAR}${qs ? `?${qs}` : ''}`,
  ).then((r) => r.data)
}

export async function obterConta(id: string): Promise<ContaResponse> {
  return api.get<ContaResponse>(`${Endpoints.financeiro.CONTAS_A_PAGAR}/${id}`).then(
    (r) => r.data,
  )
}

export async function criarConta(req: ContaRequest): Promise<ContaResponse> {
  return api.post<ContaResponse>(Endpoints.financeiro.CONTAS_A_PAGAR, req).then(
    (r) => r.data,
  )
}

export async function editarConta(
  id: string,
  req: ContaRequest,
  escopo: EscopoEdicaoSerie,
): Promise<ContaResponse> {
  return api.put<ContaResponse>(
    `${Endpoints.financeiro.CONTAS_A_PAGAR}/${id}?escopo=${escopo}`,
    req,
  ).then((r) => r.data)
}

export async function excluirConta(id: string, escopo: EscopoEdicaoSerie): Promise<void> {
  await api.delete(`${Endpoints.financeiro.CONTAS_A_PAGAR}/${id}?escopo=${escopo}`)
}

export async function registrarPagamento(
  contaId: string,
  req: RegistrarPagamentoRequest,
): Promise<PagamentoResponse> {
  return api.post<PagamentoResponse>(
    `${Endpoints.financeiro.CONTAS_A_PAGAR}/${contaId}/pagamentos`,
    req,
  ).then((r) => r.data)
}

export async function darBaixaRestante(contaId: string): Promise<ContaResponse> {
  return api.post<ContaResponse>(
    `${Endpoints.financeiro.CONTAS_A_PAGAR}/${contaId}/dar-baixa-restante`,
  ).then((r) => r.data)
}

export async function obterResumo(mesReferencia: string): Promise<ResumoResponse> {
  return api
    .get<ResumoResponse>(`${Endpoints.financeiro.CONTAS_A_PAGAR}/resumo`, {
      params: { mesReferencia },
    })
    .then((r) => r.data)
}

export async function projetarSerie(serieId: string): Promise<ProjecaoItem[]> {
  return api
    .get<ProjecaoItem[]>(`${Endpoints.financeiro.CONTAS_A_PAGAR}/series/${serieId}/projecao`)
    .then((r) => r.data)
}

// ─── Anexos ──────────────────────────────────────────────────────────────────

export async function uploadAnexo(file: File): Promise<AnexoUploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return api
    .post<AnexoUploadResponse>(Endpoints.anexos.UPLOAD, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((r) => r.data)
}

export async function deletarAnexo(id: string): Promise<void> {
  await api.delete(Endpoints.anexos.DELETE(id))
}
