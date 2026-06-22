export const Endpoints = {
    auth: {
        LOGIN: '/auth/login',
        REGISTER_IGREJA: '/igrejas/registrar',
    },

    usuarios: {
        REGISTRAR_USUARIO: '/usuarios/registrar',
        LISTAR_USUARIOS: '/usuarios',
        ATUALIZAR_USUARIO: '/usuarios',
        BY_ID: (id: string) => `/usuarios/${id}`, 
        STATUS: (id: string) => `/usuarios/${id}/status`,
        ROLE: (id: string) => `/usuarios/${id}/role`, 
    }
}