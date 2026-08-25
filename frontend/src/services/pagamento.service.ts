import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'

export interface StatusContaPagamento {
  conectada: boolean
}

export interface ConectarContaResponse {
  urlAutorizacao: string
}

export const pagamentoService = {
  buscarStatus: (): Promise<StatusContaPagamento> =>
    api.get<StatusContaPagamento>(Endpoints.pagamento.STATUS).then(res => res.data),

  gerarUrlConexao: (): Promise<ConectarContaResponse> =>
    api.get<ConectarContaResponse>(Endpoints.pagamento.CONECTAR).then(res => res.data),

  desconectar: (): Promise<void> =>
    api.delete(Endpoints.pagamento.DESCONECTAR).then(() => undefined),
}
