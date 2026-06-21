import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { UsuarioRequest, UsuarioResponse, PagedResponse } from "@/types/usuario.types";


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
}
