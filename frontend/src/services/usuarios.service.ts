import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { UsuarioRequest, UsuarioResponse, UsuarioUpdateRequest, PagedResponse } from "@/types/usuario.types";
import type { Role } from "@/types/usuario.types";

interface ListarUsuariosParams {
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const usuarioService = {
    registrarUsuario: (data: UsuarioRequest) : Promise<UsuarioResponse> =>
        api.post<UsuarioResponse>(Endpoints.usuarios.REGISTRAR_USUARIO, data).then(res => res.data),
    
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

    atualizarUsuario: (id: string, data: UsuarioUpdateRequest): Promise<UsuarioResponse> =>
        api.put<UsuarioResponse>(Endpoints.usuarios.BY_ID(id), data).then(res => res.data),

    atualizarStatus: (id: string, ativo: boolean): Promise<UsuarioResponse> =>
        api.patch<UsuarioResponse>(Endpoints.usuarios.STATUS(id), { ativo }).then(res => res.data),
    
    atualizarRole: (id: string, role: Role): Promise<UsuarioResponse> =>
        api.patch<UsuarioResponse>(Endpoints.usuarios.ROLE(id), { role }).then(res => res.data),
}
