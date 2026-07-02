import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { UsuarioResponse, PagedResponse } from "@/types/usuario.types";
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
}   

