import type { PagedResponse } from './pagedResponse.type'

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
  /** Auditoria (mesmo padrão de movimentação financeira) — quem criou/editou por último. */
  criadoPor: EventoPessoaResumo | null
  atualizadoPor: EventoPessoaResumo | null
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
  controlaPresenca: boolean
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
  /** Só pode ser true quando `requerInscricao` também é — backend recusa a combinação inversa. */
  controlaPresenca?: boolean
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
  /** Endereço já resolvido para EXIBIÇÃO (lista/detalhe): o próprio, ou o da igreja se herdado. */
  endereco: string | null
  enderecoHerdado: boolean
  /**
   * Campos CRUS do endereço próprio, para o formulário de edição reidratar fielmente. null
   * quando o local herda o endereço da igreja. Separados de `endereco` porque este colapsa os
   * dois num texto só, do qual o form não reconstrói as partes — e sem eles, editar a
   * capacidade apagava o complemento em silêncio.
   */
  cepLogradouroNumero: string | null
  complementoBairroCidadeUf: string | null
}

/**
 * Payload de criação/edição de local (POST/PUT /locais-evento). Endereço em duas partes
 * de texto livre — espelha `LocalEventoRequest` do back; ambas opcionais (local sem
 * endereço próprio herda o da igreja).
 */
export interface LocalEventoRequest {
  nome: string
  capacidade?: number | null
  cepLogradouroNumero?: string | null
  complementoBairroCidadeUf?: string | null
}

/** Mesmos dois valores de `com.domus.api.modules.evento.BaseComparacao` (back) — união de
 *  tipos, nunca string crua, para o front nunca comparar por texto solto. */
export type BaseComparacao = 'COMPARECIMENTO' | 'INSCRITOS'

/** Espelha `RelatorioEventoResponse` (relatório individual, modal/página de inscritos). */
export interface RelatorioEventoResponse {
  inscritos: { pessoas: number; convidados: number }
  /** SEMPRE calculado (não depende de controlaPresenca) — % de pessoas da igreja inscritas. */
  percentualIgrejaInscritos: number
  /** null quando o evento não controla presença — a seção de comparecimento some inteira. */
  compareceram: { pessoas: number; convidados: number } | null
  /** null pela mesma razão de `compareceram`. */
  percentualIgreja: number | null
}

/** Uma variação (evento anterior do mesmo tipo, ou média geral do filtro) com a base explícita. */
export interface VariacaoRelatorio {
  percentual: number
  base: BaseComparacao
}

export interface EventoMaisPopular {
  eventoId: string
  titulo: string
  totalInscritos: number
}

/** Um ponto do gráfico de tendência. `comparecimentoMedio` null = mês sem evento controlado. */
export interface PontoTendencia {
  /** Formato ISO "aaaa-mm", ex.: "2026-07". */
  mes: string
  comparecimentoMedio: number | null
}

export interface UltimoEventoRelatorio {
  eventoId: string
  titulo: string
  data: string
  totalParticipantes: number
  /** null quando não existe evento anterior do mesmo tipo. */
  variacaoEventoAnterior: VariacaoRelatorio | null
  variacaoMediaGeral: VariacaoRelatorio
}

/** Espelha `RelatorioGeralResponse` (página `/eventos/relatorio`). */
export interface RelatorioGeralResponse {
  resumo: {
    totalEventos: number
    /** null quando nenhum evento do filtro controla presença — nunca 0 (não mentir "ninguém foi"). */
    comparecimentoMedio: number | null
    participantesUnicos: number | null
  }
  eventoMaisPopular: EventoMaisPopular | null
  tendencia: PontoTendencia[]
  /** Paginado — resumo/eventoMaisPopular/tendencia usam o filtro inteiro, só esta lista pagina. */
  ultimosEventos: PagedResponse<UltimoEventoRelatorio>
}

/** Filtros combináveis e opcionais do relatório geral. */
export interface RelatorioGeralFiltros {
  inicio?: string
  fim?: string
  recorteEtario?: string
  tipo?: string
  page?: number
}