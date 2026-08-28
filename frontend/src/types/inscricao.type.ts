import type { PagedResponse } from './pagedResponse.type'
import type { IgrejaResumo } from './evento.type'
import type { RespostaRequest } from './campoPersonalizado.type'

export type CodigoImpedimento =
  | 'FAIXA_ETARIA'
  | 'SEM_DATA_NASCIMENTO'
  | 'EXCLUSIVO_MEMBROS'
  | 'ESTADO_CIVIL'
  | 'SEM_ESTADO_CIVIL'
  | 'SEXO'
  | 'SEM_SEXO'
  | 'VAGAS_ESGOTADAS'

export interface Impedimento {
  codigo: CodigoImpedimento
  mensagem: string
  contornavel: boolean
}

export interface ElegibilidadeResponse {
  apto: boolean
  impedimentos: Impedimento[]
}

export interface MinhaInscricaoResponse {
  id: string | null
  inscrito: boolean
  /** Task 14 — id da CobrancaEvento pendente do TITULAR (evento pago, ainda não pago).
   *  `null` quando o evento é gratuito ou não há cobrança pendente. */
  cobrancaPendenteId: string | null
}

export interface ParticipanteResponse {
  id: string
  pessoaId: string | null
  nome: string
  fotoId: string | null
  /** Preenchido só pra convidado sem cadastro (inscrição própria com pessoa_id nulo). */
  convidadoPorNome: string | null
  /** Preenchido só quando o convidado veio de um Visitante cadastrado. */
  visitanteId: string | null
  igrejaDaPessoa: IgrejaResumo
}

export interface InscritoResponse {
  id: string
  pessoaId: string | null
  nome: string
  fotoId: string | null
  pessoaRemovida: boolean
  inscritoPorUsuarioId: string | null
  inscritoPorNome: string | null
  inscritoPorFotoId: string | null
  /** Preenchido só pra convidado sem cadastro (inscrição própria com pessoa_id nulo). */
  convidadoPorNome: string | null
  /** Preenchido só pra convidado sem cadastro. */
  telefoneConvidado: string | null
  /** Preenchido só pra pessoa com cadastro. */
  telefonePessoa: string | null
  /** Preenchido só pra pessoa com cadastro. */
  emailPessoa: string | null
  inscritoEm: string
  compareceu: boolean
  igrejaDaPessoa: IgrejaResumo
  status: 'CONFIRMADA' | 'AGUARDANDO_PAGAMENTO'
  /** true = já pagou o valor original e só falta a diferença de um reajuste de preço
   *  (bem diferente de quem nunca pagou nada) — sempre false fora de AGUARDANDO_PAGAMENTO. */
  pagamentoParcial: boolean
  /** ID da cobrança com estorno pendente, ou null quando não há nenhuma (2026-08-27). Pode
   *  existir em CONFIRMADA (reajuste de preço pra baixo cujo excedente falhou ao devolver)
   *  ou em AGUARDANDO_PAGAMENTO (cancelamento/estorno em massa que falhou) — não depende do
   *  status, ao contrário de `pagamentoParcial`. */
  cobrancaEstornoPendenteId: string | null
}

export interface CriarConvidadoRequest {
  nome: string
  telefone?: string
  /** Opcional em evento gratuito, obrigatório em evento pago (usado pra mandar o
   *  comprovante de pagamento) — o backend recusa sem isso quando o evento tem preço. */
  email?: string
  /** Preenchido só quando o admin selecionou um Visitante existente na busca (aba "Visitantes"). */
  visitanteId?: string
  respostas?: RespostaRequest[]
  /** Plano 4b — evento pago: false = quem preencheu paga agora; true = gera link pra
   *  pessoa pagar sozinha depois. Sem efeito em evento gratuito. */
  gerarLink?: boolean
}

export interface ConvidadoResponse {
  inscricaoId: string
  nome: string
  telefone: string | null
  /** Plano 4b — presente só em evento pago. */
  cobrancaId: string | null
  tokenLinkPublico: string | null
}

export interface ListaInscritosResponse {
  totalPessoas: number
  vagas: number | null
  vagasRestantes: number | null
  inscritos: PagedResponse<InscritoResponse>
}

export interface InscreverPessoasRequest {
  pessoaIds: string[]
  /** Task 14 (revisão pós-review) — subconjunto de `pessoaIds` marcado como "gerar link"
   *  em `EscolhaPagamentoPorPessoa`. Vazio/ausente = todo mundo "paga agora" (comportamento
   *  anterior). Só importa em evento pago. */
  pessoasParaLink?: string[]
}

/** Item da resposta de `POST /eventos/{id}/inscricoes/pessoas` — espelha
 *  `PessoaInscritaComCobranca` (backend). `tokenLinkPublico` presente = a pessoa recebeu
 *  um link pra pagar sozinha depois; `cobrancaId` presente sem token = alguém precisa
 *  pagar agora (Payment Brick); os dois nulos = evento gratuito. */
export interface PessoaInscritaComCobranca {
  pessoaId: string
  inscricaoId: string
  cobrancaId: string | null
  tokenLinkPublico: string | null
}
