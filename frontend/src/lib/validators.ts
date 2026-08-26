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

  sexo: z.enum(['HOMEM', 'MULHER']).or(z.literal('')).optional(),

  cargo: opcional(
    z.string().max(255, 'O cargo deve ter no máximo 255 caracteres'),
  ),

  observacoes: opcional(z.string()),

  dataBatismo: opcional(
    z.string().refine(
      (val) => new Date(val) <= new Date(),
      'A data de batismo não pode estar no futuro',
    ),
  ),

  fotoId: z.string().nullable().default(null),
})

export const concederAcessoSchema = z.object({
  role: z.enum(['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'], {
    message: 'Selecione um perfil para o usuário',
  }),
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
  inicioData: z.string()
    .min(1, 'A data de início é obrigatória.')
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida.'),
  inicioHora: z.string()
    .min(1, 'A hora de início é obrigatória.')
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Horário inválido. Use o formato hh:mm.'),
  fimData: opcional(z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Data inválida.')),
  fimHora: opcional(z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Horário inválido. Use o formato hh:mm.')),
  localId: opcional(z.string()),
  localTexto: opcional(z.string()),
  tipo: opcional(z.string()),
  responsavelPessoaId: opcional(z.string()),

  fotoId: z.string().nullable().default(null),

  requerInscricao: z.boolean().default(false),
  controlaPresenca: z.boolean().default(false),
  vagas: opcionalNumero(
    z.coerce.number().int().positive('Vagas deve ser um número inteiro positivo.'),
  ),
  tipoInscricao: z.enum(['GRATUITO', 'PAGO']).default('GRATUITO'),
  preco: opcional(
    z.string().refine((v) => parseFloat(v) > 0, 'Preço deve ser maior que zero.'),
  ),
  exclusivoMembros: z.boolean().default(false),

  recorteEtario: z.string().nullable().optional(),
  idadeMin: opcionalNumero(z.coerce.number().int().min(0, 'A idade mínima não pode ser negativa.')),
  idadeMax: opcionalNumero(z.coerce.number().int().min(0, 'A idade máxima não pode ser negativa.')),
  restricaoEstadoCivil: z.enum(['SOLTEIRO', 'CASADO', 'DIVORCIADO', 'VIUVO']).nullable().optional(),
  restricaoSexo: z.enum(['HOMEM', 'MULHER']).nullable().optional(),

  restritoPropriaIgreja: z.boolean().default(false),

  repetir: z.boolean().default(false),
  recorrenciaFrequencia: z.enum(['DIARIA', 'SEMANAL', 'MENSAL'], {
    message: 'Escolha se repete por dia, semana ou mês.',
  }).optional(),
  recorrenciaIntervalo: opcionalNumero(z.coerce.number().int().positive('O intervalo deve ser maior que zero.')),
  recorrenciaDiasSemana: z.array(z.string()).default([]),
  recorrenciaTipoMensal: z.enum(['DIA_FIXO', 'DIA_DA_SEMANA'], {
    message: 'Escolha como a repetição mensal se repete.',
  }).optional(),
  recorrenciaFimTipo: z.enum(['NUNCA', 'DATA', 'CONTAGEM'], {
    message: 'Escolha quando a repetição termina.',
  }).default('NUNCA'),
  recorrenciaDataFim: opcional(z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Escolha a data final.')),
  recorrenciaNumeroOcorrencias: opcionalNumero(z.coerce.number().int().positive('Informe quantas vezes repete.')),
})

export const eventoSchema = eventoSchemaBase.refine(
  (data) => {
    if (!data.fimData || !data.fimHora) return true
    const inicio = new Date(`${data.inicioData}T${data.inicioHora}`)
    const fim = new Date(`${data.fimData}T${data.fimHora}`)
    if (isNaN(inicio.getTime()) || isNaN(fim.getTime())) return true
    return fim >= inicio
  },
  { message: 'O término não pode ser antes do início.', path: ['fimData'] }
).refine(
  (data) => {
    if (data.idadeMin == null || data.idadeMax == null) return true
    return data.idadeMin <= data.idadeMax
  },
  { message: 'A idade mínima não pode ser maior que a máxima.', path: ['idadeMax'] }
).refine(
  (data) => !data.repetir || !!data.recorrenciaFrequencia,
  { message: 'Escolha se repete por dia, semana ou mês.', path: ['recorrenciaFrequencia'] }
).refine(
  (data) => !data.repetir || (data.recorrenciaIntervalo != null && data.recorrenciaIntervalo >= 1),
  { message: 'Informe a cada quantos dias/semanas/meses repete.', path: ['recorrenciaIntervalo'] }
).refine(
  (data) => !data.repetir || data.recorrenciaFrequencia !== 'SEMANAL' || data.recorrenciaDiasSemana.length > 0,
  { message: 'Escolha pelo menos um dia da semana.', path: ['recorrenciaDiasSemana'] }
).refine(
  (data) => !data.repetir || data.recorrenciaFrequencia !== 'MENSAL' || !!data.recorrenciaTipoMensal,
  { message: 'Escolha como a repetição mensal se repete.', path: ['recorrenciaTipoMensal'] }
).refine(
  (data) => !data.repetir || data.recorrenciaFimTipo !== 'DATA' || !!data.recorrenciaDataFim,
  { message: 'Escolha a data final.', path: ['recorrenciaDataFim'] }
).refine(
  (data) => !data.repetir || data.recorrenciaFimTipo !== 'CONTAGEM' || !!data.recorrenciaNumeroOcorrencias,
  { message: 'Informe quantas vezes repete.', path: ['recorrenciaNumeroOcorrencias'] }
)

export const categoriaSchema = z.object({
  nome: z.string().trim().min(2, 'O nome da categoria deve ter pelo menos 2 caracteres').max(255, 'Máximo 255 caracteres.'),
  tipo: z.enum(['ENTRADA', 'SAIDA', 'AMBOS'], { message: 'Selecione o tipo da categoria.' }),
})

const contribuinteSchema = z.object({
  pessoaId: z.string(),
  /** Pessoa de fora, sem cadastro — exatamente um entre pessoaId/nomeExterno preenchido. */
  nomeExterno: z.string(),
  valor: z.string().min(1, 'Informe o valor.').refine((v) => parseFloat(v) > 0, 'O valor deve ser maior que zero.'),
}).refine((c) => c.pessoaId.trim() !== '' || c.nomeExterno.trim() !== '', {
  message: 'Selecione a pessoa ou digite um nome.',
  path: ['pessoaId'],
})

export const movimentacaoSchema = z.object({
  tipo: z.enum(['ENTRADA', 'SAIDA'], { message: 'Selecione o tipo.' }),
  valor: z.string()
    .min(1, 'O valor é obrigatório.')
    .refine((v) => parseFloat(v) > 0, 'O valor deve ser maior que zero.'),
  categoriaId: z.string().min(1, 'Selecione a categoria.'),
  dataMovimentacao: z.string().min(1, 'A data é obrigatória.'),
  contribuintes: z.array(contribuinteSchema),
  descricao: opcional(z.string().max(1000, 'Máximo 1000 caracteres.')),
}).superRefine((data, ctx) => {
  if (data.contribuintes.length === 0) return
  const somaContribuintes = data.contribuintes.reduce((acc, c) => acc + (parseFloat(c.valor) || 0), 0)
  const total = parseFloat(data.valor) || 0
  if (Math.abs(somaContribuintes - total) > 0.001) {
    ctx.addIssue({
      code: 'custom',
      path: ['contribuintes'],
      message: 'A soma dos contribuintes precisa ser igual ao valor da movimentação.',
    })
  }
})

export const convidadoSchema = z.object({
  nome: z.string().trim().min(2, 'O nome do convidado deve ter pelo menos 2 caracteres').max(255, 'Máximo 255 caracteres.'),
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
