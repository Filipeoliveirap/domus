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
  )

export const membroSchema = z.object({
  nome: z
    .string()
    .trim()
    .min(2, 'Nome do membro deve ter pelo menos 2 caracteres')
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

  status: z
    .enum(['ATIVO', 'INATIVO', 'VISITANTE'])
    .default('ATIVO'),

  estadoCivil: z.enum(
    ['SOLTEIRO', 'CASADO', 'DIVORCIADO', 'VIUVO']
  ).or(z.literal('')).optional(),

  ministerio: opcional(
    z.string().max(255, 'O ministério deve ter no máximo 255 caracteres'),
  ),

  observacoes: opcional(z.string()),

  batizado: z.boolean().default(false),
  dataBatismo: opcional(
    z.string().refine(
      (val) => new Date(val) <= new Date(),
      'A data de batismo não pode estar no futuro',
    ),
  ),
})

export const concederAcessoSchema = z.object({
  role: z.enum(['ADMIN_IGREJA', 'LIDER', 'MEMBRO'], {
    message: 'Selecione um perfil para o usuário',
  }),
  // Só usado quando o membro ainda não tem e-mail (o modal pede um).
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
  inicioData: z.string().min(1, 'A data de início é obrigatória.'),
  inicioHora: z.string().min(1, 'A hora de início é obrigatória.'),
  fimData: opcional(z.string()),
  fimHora: opcional(z.string()),
  local: opcional(z.string()),

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
  preco: opcionalNumero(
    z.coerce.number().positive('Preço deve ser maior que zero.'),
  ),
  exclusivoMembros: z.boolean().default(false),
  exclusivoBatizados: z.boolean().default(false),
})

export const eventoSchema = eventoSchemaBase.refine(
  (data) => {
    if (!data.fimData || !data.fimHora) return true
    const inicio = new Date(`${data.inicioData}T${data.inicioHora}`)
    const fim = new Date(`${data.fimData}T${data.fimHora}`)
    return fim >= inicio
  },
  { message: 'O término não pode ser antes do início.', path: ['fimData'] }
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
  membroId: opcional(z.string()),
  descricao: opcional(z.string().max(1000, 'Máximo 1000 caracteres.')),
})




export const convidadoSchema = z.object({
  nome: z.string().trim().min(2, 'O nome do convidado deve ter pelo menos 2 caracteres').max(255, 'Máximo 255 caracteres.'),
  telefone: opcional(
    z.string().regex(
      /^\(\d{2}\)\s\d{4,5}-\d{4}$/,
      'Telefone inválido. Use o formato (00) 00000-0000',
    ),
  ),
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

export type LoginFormData = z.infer<typeof loginSchema>
export type RegistrarIgrejaFormData1 = z.infer<typeof registrarIgrejaSchema1>
export type RegistrarIgrejaFormData2 = z.infer<typeof registrarIgrejaSchema2>
export type MembroFormData = z.infer<typeof membroSchema> 
export type MembroFormInput = z.input<typeof membroSchema>  
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
export type EsqueciSenhaFormData = z.infer<typeof esqueciSenhaSchema>
export type RedefinirSenhaFormData = z.infer<typeof redefinirSenhaSchema>
