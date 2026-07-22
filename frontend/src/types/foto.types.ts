export type TipoFoto = 'MEMBRO' | 'EVENTO' | 'IGREJA'

export interface FotoResponse {
  id: string
  tipo: TipoFoto
  bytes: number
}
