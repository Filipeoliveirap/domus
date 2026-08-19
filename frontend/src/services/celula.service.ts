import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  CelulaResponse, CelulaDetalheResponse, CelulaRequest,
  AdicionarMembroCelulaRequest, ConverterVisitanteRequest,
} from '@/types/celula.type'
import type { PessoaResponse } from '@/types/pessoa.type'

export const celulaService = {
  listar: (): Promise<CelulaResponse[]> =>
    api.get<CelulaResponse[]>(Endpoints.celulas.LISTAR).then(res => res.data),

  buscar: (id: string): Promise<CelulaDetalheResponse> =>
    api.get<CelulaDetalheResponse>(Endpoints.celulas.BY_ID(id)).then(res => res.data),

  criar: (data: CelulaRequest): Promise<CelulaResponse> =>
    api.post<CelulaResponse>(Endpoints.celulas.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: CelulaRequest): Promise<CelulaResponse> =>
    api.put<CelulaResponse>(Endpoints.celulas.BY_ID(id), data).then(res => res.data),

  excluir: (id: string): Promise<void> =>
    api.delete(Endpoints.celulas.BY_ID(id)).then(() => undefined),

  listarArquivadas: (): Promise<CelulaResponse[]> =>
    api.get<CelulaResponse[]>(Endpoints.celulas.ARQUIVADOS).then(res => res.data),

  restaurar: (id: string): Promise<void> =>
    api.post(Endpoints.celulas.RESTAURAR(id)).then(() => undefined),

  excluirDefinitivo: (id: string): Promise<void> =>
    api.delete(Endpoints.celulas.DEFINITIVO(id)).then(() => undefined),

  adicionarMembro: (celulaId: string, data: AdicionarMembroCelulaRequest): Promise<void> =>
    api.post(Endpoints.celulas.MEMBROS(celulaId), data).then(() => undefined),

  removerMembro: (celulaId: string, membroId: string): Promise<void> =>
    api.delete(Endpoints.celulas.MEMBRO(celulaId, membroId)).then(() => undefined),

  atualizarPapel: (celulaId: string, membroId: string, papel: 'LIDER' | 'MEMBRO'): Promise<void> =>
    api.put(Endpoints.celulas.PAPEL(celulaId, membroId), { papel }).then(() => undefined),

  converterVisitante: (celulaId: string, visitanteId: string, data: ConverterVisitanteRequest): Promise<PessoaResponse> =>
    api.post<PessoaResponse>(Endpoints.celulas.CONVERTER(celulaId, visitanteId), data).then(res => res.data),
}
