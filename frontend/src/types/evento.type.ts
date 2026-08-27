import type { PagedResponse } from './pagedResponse.type'

export type SituacaoEvento = 'AGENDADO' | 'EM_ANDAMENTO' | 'ENCERRADO'

export type RestricaoEstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'

export type RestricaoSexo = 'HOMEM' | 'MULHER'

export interface EventoArquivadoResponse {
  id: string
  titulo: string
  inicioEm: string
  tipo: string | null
  temVinculo: boolean
  totalInscritos: number
  serieId: string | null
}

export interface EventoLocalInfo {
  id: string | null
  nome: string
  endereco: string | null
  enderecoHerdado: boolean
}

export interface EventoPessoaResumo {
  id: string | null
  nome: string
}

export interface IgrejaResumo {
  id: string
  nome: string
  sigla: string | null
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
  criadoPor: EventoPessoaResumo | null
  atualizadoPor: EventoPessoaResumo | null
  fotoId: string | null
  createdAt: string
  vagas: number | null
  preco: number | null
  exclusivoMembros: boolean
  requerInscricao: boolean
  controlaPresenca: boolean
  situacao: SituacaoEvento
  inscricoesRemovidas: number | null
  recorteEtario: string | null
  idadeMin: number | null
  idadeMax: number | null
  restricaoEstadoCivil: RestricaoEstadoCivil | null
  restricaoSexo: RestricaoSexo | null
  igrejaOrganizadora: IgrejaResumo
  podeGerenciarEsteEvento: boolean
  restritoPropriaIgreja: boolean
  arquivado: boolean
  serieId: string | null
  divergeDaSerie: boolean
}

export type FrequenciaRecorrencia = 'DIARIA' | 'SEMANAL' | 'MENSAL'
export type TipoRecorrenciaMensal = 'DIA_FIXO' | 'DIA_DA_SEMANA'
export type EscopoEdicaoEvento = 'ESTA' | 'ESTA_E_SEGUINTES' | 'SERIE'
export type DiaSemana = 'SEGUNDA' | 'TERCA' | 'QUARTA' | 'QUINTA' | 'SEXTA' | 'SABADO' | 'DOMINGO'

export interface RecorrenciaRequest {
  frequencia: FrequenciaRecorrencia
  intervalo: number
  diasSemana?: DiaSemana[]
  tipoRecorrenciaMensal?: TipoRecorrenciaMensal | null
  dataFim?: string | null
  numeroOcorrencias?: number | null
}

export interface EventoRequest {
  titulo: string
  descricao?: string
  inicioEm: string
  fimEm?: string
  localId?: string
  localTexto?: string
  tipo?: string
  responsavelPessoaId?: string | null
  fotoId?: string | null
  vagas?: number
  preco?: string
  exclusivoMembros?: boolean
  requerInscricao?: boolean
  controlaPresenca?: boolean
  recorteEtario?: string | null
  idadeMin?: number | null
  idadeMax?: number | null
  restricaoEstadoCivil?: RestricaoEstadoCivil | null
  restricaoSexo?: RestricaoSexo | null
  restritoPropriaIgreja?: boolean
  recorrencia?: RecorrenciaRequest | null
}

export interface InscritoImpactado {
  pessoaId: string
  nome: string
  motivos: string[]
}

export interface ImpactoRestricaoResponse {
  afetados: InscritoImpactado[]
}

/** Espelha `ImpactoMudancaPrecoResponse` (backend) — prévia de estorno/cobrança ao mudar
 *  preço, sem gravar nada nem chamar o Mercado Pago. `SEM_IMPACTO` = sem mudança real de
 *  direção ou sem ninguém afetado. Campos de uma direção nunca vêm preenchidos junto com
 *  os da outra — cada mudança de preço só anda numa direção por vez. */
export type TipoImpactoMudancaPreco = 'SEM_IMPACTO' | 'PAGO_PARA_GRATUITO' | 'GRATUITO_PARA_PAGO'

export interface ImpactoMudancaPrecoResponse {
  tipo: TipoImpactoMudancaPreco
  /** PAGO_PARA_GRATUITO: quem já pagou e seria estornado. */
  pessoasComPagamentoPago: number
  /** PAGO_PARA_GRATUITO: soma do que seria estornado de verdade no Mercado Pago. */
  valorTotalAEstornar: number
  /** PAGO_PARA_GRATUITO: quem estava aguardando pagamento e seria confirmado direto. */
  pessoasAguardandoPagamento: number
  /** GRATUITO_PARA_PAGO: quantas pessoas já confirmadas ganhariam uma cobrança nova. */
  pessoasSeraoCobradas: number
  /** GRATUITO_PARA_PAGO: soma do que seria cobrado (pessoasSeraoCobradas × preço novo). */
  valorTotalACobrar: number
}

export interface LocalEventoResponse {
  id: string
  nome: string
  capacidade: number | null
  endereco: string | null
  enderecoHerdado: boolean
  cepLogradouroNumero: string | null
  complementoBairroCidadeUf: string | null
  temEvento: boolean
}

export interface LocalEventoRequest {
  nome: string
  capacidade?: number | null
  cepLogradouroNumero?: string | null
  complementoBairroCidadeUf?: string | null
}

export type BaseComparacao = 'COMPARECIMENTO' | 'INSCRITOS'

export interface RelatorioEventoResponse {
  inscritos: { pessoas: number; convidados: number }
  percentualIgrejaInscritos: number
  compareceram: { pessoas: number; convidados: number } | null
  percentualIgreja: number | null
}

export interface VariacaoRelatorio {
  percentual: number
  base: BaseComparacao
}

export interface EventoMaisPopular {
  eventoId: string
  titulo: string
  totalInscritos: number
}

export interface PontoTendencia {
  mes: string
  comparecimentoMedio: number | null
}

export interface UltimoEventoRelatorio {
  eventoId: string
  titulo: string
  data: string
  totalParticipantes: number
  variacaoEventoAnterior: VariacaoRelatorio | null
  variacaoMediaGeral: VariacaoRelatorio
}

export interface RelatorioGeralResponse {
  resumo: {
    totalEventos: number
    comparecimentoMedio: number | null
    participantesUnicos: number | null
  }
  eventoMaisPopular: EventoMaisPopular | null
  tendencia: PontoTendencia[]
  ultimosEventos: PagedResponse<UltimoEventoRelatorio>
}

export interface RelatorioGeralFiltros {
  inicio?: string
  fim?: string
  recorteEtario?: string
  tipo?: string
  page?: number
}
