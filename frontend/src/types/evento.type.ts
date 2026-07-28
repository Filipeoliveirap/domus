import type { PagedResponse } from './pagedResponse.type'

export type SituacaoEvento = 'AGENDADO' | 'EM_ANDAMENTO' | 'ENCERRADO'

export type RestricaoEstadoCivil = 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO'

export type RestricaoSexo = 'HOMEM' | 'MULHER'

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
  restritoPropriaIgreja?: boolean
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
}

export interface InscritoImpactado {
  pessoaId: string
  nome: string
  motivos: string[]
}

export interface ImpactoRestricaoResponse {
  afetados: InscritoImpactado[]
}

export interface LocalEventoResponse {
  id: string
  nome: string
  capacidade: number | null
  endereco: string | null
  enderecoHerdado: boolean
  cepLogradouroNumero: string | null
  complementoBairroCidadeUf: string | null
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
