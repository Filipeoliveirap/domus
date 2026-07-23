import {z} from 'zod'

export const loginSchema = z.object({
    email: z.email('Digite um E-mail válido').min(1, 'E-mail é obrigatório'),
    senha: z.string().min(1, 'Senha é obrigatória')
})

export const registrarIgrejaSchema1 = z.object({
  nomeIgreja: z.string().trim().min(2, 'Nome da igreja deve ter pelo menos 2 caracteres').max(255, 'Nome da igreja deve ter no máximo 255 caracteres'),
  emailContato: z.email('E-mail de contato inválido').min(1, 'E-mail de contato é obrigatório').transform((v) => v.trim().toLowerCase()),
  cnpj: z.string().trim().optional(),
  telefoneContato: z
  .string()
  .trim()
  .min(1, 'Telefone de contato é obrigatório')
  .regex(
    /^\(\d{2}\)\s\d{4,5}-\d{4}$/,
    'Telefone inválido. Use o formato (00) 00000-0000',
  ),
  
})

export const registrarIgrejaSchema2 = z.object({
  nomeAdmin: z.string().trim().min(2, 'Nome do administrador deve ter pelo menos 2 caracteres').max(255, 'Nome do administrador deve ter no máximo 255 caracteres'),
  emailAdmin: z.email('E-mail inválido').min(1, 'E-mail é obrigatório').transform((v) => v.trim().toLowerCase()),
  senhaAdmin: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarSenha: z.string().min(1, 'Confirme a senha'),
  aceitouTermos: z.boolean().refine((val) => val === true, {
    message: 'Você precisa aceitar os termos',
  })
}).refine(data => data.senhaAdmin === data.confirmarSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarSenha'],
})

/**
 * Campo opcional que aceita string vazia como "não preenchido" (comum em formulário HTML,
 * onde um input sem valor manda `''`, não `undefined`).
 *
 * O `.optional()` de FORA (não só o de dentro do `preprocess`) é o que importa: sem ele, um
 * campo cujo NOME nunca chega a existir no objeto validado (não `''`, literalmente ausente —
 * o caso de um campo só controlado via `setValue`, nunca `register()`ado, que o usuário nunca
 * chega a tocar, ex.: data de batismo quando o vínculo começa como CONGREGANTE) falha com
 * "Invalid input: expected nonoptional, received undefined" pra TODO campo opcional do
 * formulário de uma vez — o Zod v4 só reconhece a chave do objeto como opcional quando o tipo
 * mais externo do campo é `ZodOptional`; `preprocess(fn, schema.optional())` tem `ZodPipe` por
 * fora, não conta. Envolver o resultado inteiro em `.optional()` resolve os dois casos (chave
 * ausente E valor vazio) sem reintroduzir o problema oposto (ver commit que corrigiu isto).
 */
const opcional = <T extends z.ZodType<string>>(schema: T) =>
  z.preprocess(
    (val) => (val === '' || val == null ? undefined : val),
    schema.optional(),
  ).optional()

export const pessoaSchema = z.object({
  nome: z
    .string()
    .trim()
    .min(2, 'Nome da pessoa deve ter pelo menos 2 caracteres')
    .max(255, 'O nome deve ter no máximo 255 caracteres'),

  email: opcional(
    z.email('E-mail inválido').min(1, 'E-mail é obrigatório').transform((v) => v.trim().toLowerCase()),
  ),

  telefone: opcional(
    z.string().regex(
      /^\(\d{2}\)\s\d{4,5}-\d{4}$/,
      'Telefone inválido. Use o formato (00) 00000-0000',
    ),
  ),

  dataNascimento: opcional(
    z.string().refine(
      (val) => new Date(val) < new Date(),
      'A data de nascimento deve estar no passado',
    ),
  ),

  endereco: z.object({
    cep: opcional(z.string().regex(/^\d{5}-\d{3}$/, 'CEP inválido. Use o formato 00000-000')),
    logradouro: opcional(z.string().max(255)),
    numero: opcional(z.string().max(20)),
    complemento: opcional(z.string().max(255)),
    bairro: opcional(z.string().max(255)),
    cidade: opcional(z.string().max(255)),
    uf: opcional(z.string().length(2, 'UF deve ter 2 letras')),
  }).optional(),

  vinculo: z
    .enum(['MEMBRO', 'CONGREGANTE'])
    .default('CONGREGANTE'),

  estadoCivil: z.enum(
    ['SOLTEIRO', 'CASADO', 'DIVORCIADO', 'VIUVO']
  ).or(z.literal('')).optional(),

  // Nulável de propósito: pessoas já cadastradas não têm valor. Usado só pra
  // restringir inscrição em evento, não pra descrever identidade.
  sexo: z.enum(['HOMEM', 'MULHER']).or(z.literal('')).optional(),

  ministerio: opcional(
    z.string().max(255, 'O ministério deve ter no máximo 255 caracteres'),
  ),

  observacoes: opcional(z.string()),

  // Só faz sentido quando vinculo === 'MEMBRO' — a UI esconde o campo para CONGREGANTE.
  dataBatismo: opcional(
    z.string().refine(
      (val) => new Date(val) <= new Date(),
      'A data de batismo não pode estar no futuro',
    ),
  ),

  // Id da foto já enviada via POST /fotos (ver UploadFoto). null = sem foto.
  fotoId: z.string().nullable().default(null),
})

export const concederAcessoSchema = z.object({
  role: z.enum(['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'], {
    message: 'Selecione um perfil para o usuário',
  }),
  // Só usado quando a pessoa ainda não tem e-mail (o modal pede um).
  email: opcional(
    z.email('E-mail inválido').transform((v) => v.trim().toLowerCase()),
  ),
})

const opcionalNumero = <T extends z.ZodType<number>>(schema: T) =>
  z.preprocess(
    (val) => (val === '' || val == null ? undefined : val),
    schema.optional(),
  )

const eventoSchemaBase = z.object({
  titulo: z.string().trim().min(1, 'O título é obrigatório.'),
  descricao: opcional(z.string()),
  // ISO (aaaa-mm-dd): é o que o CampoData guarda e o que o backend espera. A exibição em
  // dd/mm/aaaa é assunto interno do componente, não do formulário.
  inicioData: z.string()
    .min(1, 'A data de início é obrigatória.')
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida.'),
  inicioHora: z.string()
    .min(1, 'A hora de início é obrigatória.')
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Horário inválido. Use o formato hh:mm.'),
  fimData: opcional(z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida.')),
  fimHora: opcional(z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Horário inválido. Use o formato hh:mm.')),
  // Local em duas formas mutuamente exclusivas: `localId` aponta um LocalEvento cadastrado;
  // `localTexto` é o ad-hoc ("chácara do João"). O <SeletorLocal> garante que só uma esteja
  // preenchida por vez, e o backend recusa as duas juntas (LOCAL_AMBIGUO).
  localId: opcional(z.string()),
  localTexto: opcional(z.string()),
  // Texto livre com sugestões (GET /eventos/tipos). NÃO é a categoria financeira.
  tipo: opcional(z.string()),
  // Pessoa responsável pelo evento. Vazio = sem responsável definido.
  responsavelPessoaId: opcional(z.string()),

  // Id da foto (capa do evento) já enviada via POST /fotos. null = sem foto.
  fotoId: z.string().nullable().default(null),

  // ─── Inscrições (Fase 2) ───
  // requerInscricao é o "interruptor mestre": quando false, os demais campos desta seção
  // ficam ocultos na UI, mas continuam registrados no RHF (não são desmontados/unregister —
  // o form não usa shouldUnregister), então o valor atual sempre viaja no payload do PUT.
  requerInscricao: z.boolean().default(false),
  vagas: opcionalNumero(
    z.coerce.number().int().positive('Vagas deve ser um número inteiro positivo.'),
  ),
  // Campo só de UI (não existe no backend): decide se `preco` é enviado ou limpo.
  tipoInscricao: z.enum(['GRATUITO', 'PAGO']).default('GRATUITO'),
  // Canônico como string "12.34" (igual ao `valor` de movimentação financeira) — o mesmo
  // formato que `EventoResponse.preco` já usa na API (BigDecimal serializado como string).
  // O input usa a mesma máscara de dinheiro (da direita para a esquerda) do financeiro.
  preco: opcional(
    z.string().refine((v) => parseFloat(v) > 0, 'Preço deve ser maior que zero.'),
  ),
  // "Exclusivo para membros" é vocabulário de domínio (vínculo com a igreja), não do
  // cadastro — barra quem tem vínculo CONGREGANTE.
  exclusivoMembros: z.boolean().default(false),

  // ─── "Para quem é" (elegibilidade, Task 9) ───
  // recorteEtario é só o NOME do chip escolhido (Kids, Jovens...) — alimenta o selo no
  // card e é reaplicado ao reidratar; não valida nada sozinho, quem restringe de fato
  // são idadeMin/idadeMax.
  recorteEtario: z.string().nullable().optional(),
  idadeMin: opcionalNumero(z.coerce.number().int().min(0, 'A idade mínima não pode ser negativa.')),
  idadeMax: opcionalNumero(z.coerce.number().int().min(0, 'A idade máxima não pode ser negativa.')),
  restricaoEstadoCivil: z.enum(['SOLTEIRO', 'CASADO', 'DIVORCIADO', 'VIUVO']).nullable().optional(),
  restricaoSexo: z.enum(['HOMEM', 'MULHER']).nullable().optional(),
})

export const eventoSchema = eventoSchemaBase.refine(
  (data) => {
    if (!data.fimData || !data.fimHora) return true
    const inicio = new Date(`${data.inicioData}T${data.inicioHora}`)
    const fim = new Date(`${data.fimData}T${data.fimHora}`)
    if (isNaN(inicio.getTime()) || isNaN(fim.getTime())) return true // o regex já pega formato
    return fim >= inicio
  },
  { message: 'O término não pode ser antes do início.', path: ['fimData'] }
).refine(
  (data) => {
    if (data.idadeMin == null || data.idadeMax == null) return true
    return data.idadeMin <= data.idadeMax
  },
  { message: 'A idade mínima não pode ser maior que a máxima.', path: ['idadeMax'] }
)

export const categoriaSchema = z.object({
  nome: z.string().trim().min(2, 'O nome da categoria deve ter pelo menos 2 caracteres').max(255, 'Máximo 255 caracteres.'),
  tipo: z.enum(['ENTRADA', 'SAIDA', 'AMBOS'], { message: 'Selecione o tipo da categoria.' }),
})

export const movimentacaoSchema = z.object({
  tipo: z.enum(['ENTRADA', 'SAIDA'], { message: 'Selecione o tipo.' }),
  valor: z.string()
    .min(1, 'O valor é obrigatório.')
    .refine((v) => parseFloat(v) > 0, 'O valor deve ser maior que zero.'),
  categoriaId: z.string().min(1, 'Selecione a categoria.'),
  dataMovimentacao: z.string().min(1, 'A data é obrigatória.'),
  pessoaId: opcional(z.string()),
  descricao: opcional(z.string().max(1000, 'Máximo 1000 caracteres.')),
})




export const convidadoSchema = z.object({
  nome: z.string().trim().min(2, 'O nome do convidado deve ter pelo menos 2 caracteres').max(255, 'Máximo 255 caracteres.'),
  // Aceita qualquer forma de digitação (com/sem espaço, com/sem máscara) — a UI já formata
  // visualmente com `formatarTelefone` (lib/masks) a cada tecla; aqui só validamos a
  // quantidade de dígitos e enviamos ao backend só os dígitos (sem máscara).
  telefone: opcional(
    z.string()
      .transform((v) => v.replace(/\D/g, ''))
      .refine((v) => v.length === 10 || v.length === 11, {
        message: 'Telefone inválido. Digite um número válido com DDD.',
      }),
  ),
})

export const localEventoSchema = z.object({
  nome: z.string().trim().min(1, 'O nome do local é obrigatório.').max(150, 'Máximo 150 caracteres.'),
  capacidade: opcionalNumero(z.coerce.number().int().positive('A capacidade deve ser maior que zero.')),
  // Texto livre (não é ViaCEP estruturado) — mesmo par de campos do back
  // (cepLogradouroNumero/complementoBairroCidadeUf). Vazio = local herda o endereço da igreja.
  cepLogradouroNumero: opcional(z.string().max(255, 'Máximo 255 caracteres.')),
  complementoBairroCidadeUf: opcional(z.string().max(255, 'Máximo 255 caracteres.')),
})

export const esqueciSenhaSchema = z.object({
  email: z.email('Digite um E-mail válido').min(1, 'E-mail é obrigatório').transform((v) => v.trim().toLowerCase()),
})

export const redefinirSenhaSchema = z.object({
  novaSenha: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarSenha: z.string().min(1, 'Confirme a senha'),
}).refine(data => data.novaSenha === data.confirmarSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarSenha'],
})

export const alterarSenhaSchema = z.object({
  senhaAtual: z.string().min(1, 'Senha atual é obrigatória'),
  novaSenha: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarNovaSenha: z.string().min(1, 'Confirme a nova senha'),
}).refine(data => data.novaSenha === data.confirmarNovaSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarNovaSenha'],
})

export type AlterarSenhaFormData = z.infer<typeof alterarSenhaSchema>

export type LoginFormData = z.infer<typeof loginSchema>
export type RegistrarIgrejaFormData1 = z.infer<typeof registrarIgrejaSchema1>
export type RegistrarIgrejaFormData2 = z.infer<typeof registrarIgrejaSchema2>
export type PessoaFormData = z.infer<typeof pessoaSchema>
export type PessoaFormInput = z.input<typeof pessoaSchema>
export type ConcederAcessoFormData = z.infer<typeof concederAcessoSchema>
export type ConcederAcessoFormInput = z.input<typeof concederAcessoSchema>
export type EventoFormData = z.infer<typeof eventoSchema>
export type EventoFormInput = z.input<typeof eventoSchemaBase>
export type CategoriaFormData = z.infer<typeof categoriaSchema>
export type CategoriaFormInput = z.input<typeof categoriaSchema>
export type MovimentacaoFormData = z.infer<typeof movimentacaoSchema>
export type MovimentacaoFormInput = z.input<typeof movimentacaoSchema>
export type ConvidadoFormData = z.infer<typeof convidadoSchema>
export type ConvidadoFormInput = z.input<typeof convidadoSchema>
export type LocalEventoFormData = z.infer<typeof localEventoSchema>
export type LocalEventoFormInput = z.input<typeof localEventoSchema>
export type EsqueciSenhaFormData = z.infer<typeof esqueciSenhaSchema>
export type RedefinirSenhaFormData = z.infer<typeof redefinirSenhaSchema>
