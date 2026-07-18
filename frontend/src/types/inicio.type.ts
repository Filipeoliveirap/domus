export interface EventoResumo {
  id: string
  titulo: string
  inicio: string
  local: string | null
}

export interface Aniversariante {
  id: string
  nome: string
  dia: number
}

export interface InicioResponse {
  aniversariantesMes: Aniversariante[]
  proximosEventos: EventoResumo[]
}
