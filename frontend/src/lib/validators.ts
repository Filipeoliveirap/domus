import {z} from 'zod'

// ─── Auth 

export const loginSchema = z.object({
    email: z.email('Digite um email válido').min(1, 'E-mail é obrigatório'),
    senha: z.string().min(1, 'Senha é obrigatória')
})

export const registrarIgrejaSchema1 = z.object({
  nomeIgreja: z.string().trim().min(1, 'Nome da igreja é obrigatório'),
  emailContato: z.email('E-mail de contato é obrigatório').min(1, 'E-mail de contato é obrigatório'),
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
  nomeAdmin: z.string().min(1, 'Nome é obrigatório'),
  emailAdmin: z.email('E-mail inválido').min(1, 'E-mail é obrigatório'),
  senhaAdmin: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarSenha: z.string().min(1, 'Confirme a senha'),
  aceitouTermos: z.boolean().refine((val) => val === true, {
    message: 'Você precisa aceitar os termos',
  })
}).refine(data => data.senhaAdmin === data.confirmarSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarSenha'],
})

// Membro

const opcional = <T extends z.ZodType<string>>(schema: T) =>
  z.preprocess(
    (val) => (val === '' || val == null ? undefined : val),
    schema.optional(),
  )

export const membroSchema = z.object({
  nome: z
    .string()
    .trim()
    .min(1, 'Nome é obrigatório')
    .max(255, 'O nome deve ter no máximo 255 caracteres'),

  email: opcional(
    z.email('E-mail inválido').max(255, 'O e-mail deve ter no máximo 255 caracteres'),
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

  endereco: opcional(
    z.string().max(500, 'O endereço deve ter no máximo 500 caracteres'),
  ),

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
})

export const concederAcessoSchema = z.object({
  role: z.enum(['ADMIN_IGREJA', 'LIDER', 'MEMBRO'], {
    message: 'Selecione um perfil para o usuário',
  }),
  senha: z
    .string()
    .min(8, 'A senha deve ter no mínimo 8 caracteres'),
  confirmarSenha: z
    .string()
    .min(1, 'Confirme a senha'),
}).refine(data => data.senha === data.confirmarSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarSenha'],
})

const eventoSchemaBase = z.object({
  titulo: z.string().min(1, 'O título é obrigatório.'),
  descricao: opcional(z.string()),
  inicioData: z.string().min(1, 'A data de início é obrigatória.'),
  inicioHora: z.string().min(1, 'A hora de início é obrigatória.'),
  fimData: opcional(z.string()),
  fimHora: opcional(z.string()),
  local: opcional(z.string()),
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




export type LoginFormData = z.infer<typeof loginSchema>
export type RegistrarIgrejaFormData1 = z.infer<typeof registrarIgrejaSchema1>
export type RegistrarIgrejaFormData2 = z.infer<typeof registrarIgrejaSchema2>
export type MembroFormData = z.infer<typeof membroSchema> 
export type MembroFormInput = z.input<typeof membroSchema>  
export type ConcederAcessoFormData = z.infer<typeof concederAcessoSchema>
export type EventoFormData = z.infer<typeof eventoSchema>
export type EventoFormInput = z.input<typeof eventoSchemaBase>
