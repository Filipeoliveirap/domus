/** Situação derivada de inicioEm/fimEm no backend (com.domus.api.modules.evento.SituacaoEvento). */
export type SituacaoEvento = 'AGENDADO' | 'EM_ANDAMENTO' | 'ENCERRADO'

/** Espelha com.domus.api.modules.pessoa.EstadoCivil (os quatro valores). Na prática a restrição de evento usa SOLTEIRO e CASADO, mas o tipo cobre o enum inteiro. */
export type RestricaoEstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'

/** Espelha com.domus.api.modules.pessoa.Sexo. */
export type RestricaoSexo = 'HOMEM' | 'MULHER'

/**
 * Local do evento na resposta: `id` null significa local ad-hoc (o nome veio de texto
 * livre, não de um `LocalEvento` cadastrado) — espelha `EventoResponse.LocalInfo` do back.
 */
export interface EventoLocalInfo {
  id: string | null
  nome: string
  endereco: string | null
  enderecoHerdado: boolean
}

/** Resumo de pessoa/usuário: `id` null = registro arquivado, sobrou só o nome congelado. */
export interface EventoPessoaResumo {
  id: string | null
  nome: string
}

export interface EventoResponse {
  id: string
  titulo: string
  descricao: string | null
  inicioEm: string
  fimEm: string | null
  local: EventoLocalInfo | null
  tipo: string | null
  responsavel: EventoPessoaResumo | null
  fotoId: string | null
  createdAt: string
  vagas: number | null
  /**
   * Chega como NÚMERO no JSON (`"preco":50.00`) — o Jackson serializa `BigDecimal` assim.
   * Tipar como string era mentira e quebrava a edição: o Zod do formulário exige string e
   * recusava o valor vindo da API com "expected string, received number".
   */
  preco: number | null
  exclusivoMembros: boolean
  requerInscricao: boolean
  situacao: SituacaoEvento
  /** Só populado na resposta de `atualizarEvento`; null nas demais. */
  inscricoesRemovidas: number | null
  /** Nome do recorte etário escolhido (Kids, Jovens…) — alimenta o selo, não valida nada. */
  recorteEtario: string | null
  idadeMin: number | null
  idadeMax: number | null
  restricaoEstadoCivil: RestricaoEstadoCivil | null
  restricaoSexo: RestricaoSexo | null
}

export interface EventoRequest {
  titulo: string
  descricao?: string
  inicioEm: string
  fimEm?: string
  /** Local cadastrado. Mutuamente exclusivo com `localTexto` — nunca envie os dois. */
  localId?: string
  /** Local ad-hoc ("chácara do João"). Mutuamente exclusivo com `localId`. */
  localTexto?: string
  /** Texto livre com sugestões (GET /eventos/tipos). Não é a categoria financeira. */
  tipo?: string
  /** Pessoa responsável pelo evento; ausente/null = sem responsável definido. */
  responsavelPessoaId?: string | null
  fotoId?: string | null
  vagas?: number
  preco?: string
  exclusivoMembros?: boolean
  requerInscricao?: boolean
  /** Nome do recorte etário (Kids, Jovens…); só decorativo/filtro, não valida. */
  recorteEtario?: string | null
  idadeMin?: number | null
  idadeMax?: number | null
  restricaoEstadoCivil?: RestricaoEstadoCivil | null
  restricaoSexo?: RestricaoSexo | null
}

/**
 * Prévia de quem ficaria de fora ao apertar/ligar a restrição — nunca grava nada, só
 * informa o admin para ele decidir manter ou cancelar (espelha ImpactoRestricaoResponse).
 */
export interface InscritoImpactado {
  pessoaId: string
  nome: string
  /** O mesmo texto do 422 de elegibilidade (Impedimento.mensagem()). */
  motivos: string[]
}

export interface ImpactoRestricaoResponse {
  afetados: InscritoImpactado[]
}

/** Local cadastrado que aparece no `<SeletorLocal>` (GET /locais-evento). */
export interface LocalEventoResponse {
  id: string
  nome: string
  capacidade: number | null
  endereco: string | null
  enderecoHerdado: boolean
}