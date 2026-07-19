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
  /** Nulo enquanto o upload de foto (Fase 2) não existir — a tela cai nas iniciais. */
  foto: string | null
}

export interface InicioResponse {
  aniversariantesMes: Aniversariante[]
  proximosEventos: EventoResumo[]
}
