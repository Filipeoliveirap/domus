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
  /** Nulo até a pessoa ter foto — a tela cai nas iniciais. */
  fotoId: string | null
}

export interface InicioResponse {
  aniversariantesMes: Aniversariante[]
  proximosEventos: EventoResumo[]
}
