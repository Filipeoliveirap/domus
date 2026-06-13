import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { UsuarioRequest, UsuarioResponse } from "@/types/usuario.types";

export const usuarioService = {
    registrarUsuario: (data: UsuarioRequest) : Promise<UsuarioResponse> =>
        api.post<UsuarioResponse>(Endpoints.usuarios.REGISTRAR_USUARIO, data).then(res => res.data),
    
}