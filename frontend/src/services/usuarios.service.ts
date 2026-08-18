import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { UsuarioResponse, UsuarioArquivadoResponse } from "@/types/usuario.types";
import { PagedResponse } from "@/types/pagedResponse.type";
import type { Role } from "@/types/usuario.types";

interface ListarUsuariosParams {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const usuarioService = {
    listarUsuarios: (params: ListarUsuariosParams) : Promise<PagedResponse<UsuarioResponse>> =>
        api.get<PagedResponse<UsuarioResponse>>(Endpoints.usuarios.LISTAR_USUARIOS, { params : {
            q: params.q || undefined,
            page: params.page ?? 0,
            size: params.size ?? 20,
            sort: params.sort ?? "nome,asc",
        }, 
    }).then(res => res.data),

    buscarUsuario: (id: string): Promise<UsuarioResponse> =>
        api.get<UsuarioResponse>(Endpoints.usuarios.BY_ID(id)).then(res => res.data),

    atualizarStatus: (id: string, ativo: boolean): Promise<UsuarioResponse> =>
        api.patch<UsuarioResponse>(Endpoints.usuarios.STATUS(id), { ativo }).then(res => res.data),
    
    atualizarRole: (id: string, role: Role): Promise<UsuarioResponse> =>
        api.patch<UsuarioResponse>(Endpoints.usuarios.ROLE(id), { role }).then(res => res.data),

    arquivarUsuario: (id: string): Promise<void> =>
        api.delete(Endpoints.usuarios.BY_ID(id)).then(() => undefined),

    concederCapacidade: (id: string, capacidade: string): Promise<void> =>
        api.post(Endpoints.usuarios.CAPACIDADE(id), { capacidade }).then(() => undefined),

    revogarCapacidade: (id: string, capacidade: string): Promise<void> =>
        api.delete(Endpoints.usuarios.CAPACIDADE_ESPECIFICA(id, capacidade)).then(() => undefined),

    listarArquivados: (): Promise<UsuarioArquivadoResponse[]> =>
        api.get<UsuarioArquivadoResponse[]>(Endpoints.usuarios.ARQUIVADOS).then(res => res.data),

    restaurar: (id: string): Promise<void> =>
        api.post(Endpoints.usuarios.RESTAURAR(id)).then(() => undefined),

    excluirDefinitivo: (id: string): Promise<void> =>
        api.delete(Endpoints.usuarios.DEFINITIVO(id)).then(() => undefined),
}

