import {z} from 'zod'

// ─── Auth ──────────────────────────────────────────────────────

export const loginSchema = z.object({
    email: z
    .string()
    .min(1, 'Email é obrigatório')
    .email('Digite um email válido'),
    senha: z
    .string()
    .min(1, 'Senha é obrigatória')
})

export const registrarIgrejaSchema1 = z.object({
  nomeIgreja: z.string().trim().min(1, 'Nome da igreja é obrigatório'),
  emailContato: z.string().trim().min(1, 'E-mail de contato é obrigatório').email('E-mail inválido'),
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
  emailAdmin: z.string().email('E-mail inválido'),
  senhaAdmin: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarSenha: z.string().min(1, 'Confirme a senha'),
  aceitouTermos: z.boolean().refine((val) => val === true, {
    message: 'Você precisa aceitar os termos',
  })
}).refine(data => data.senhaAdmin === data.confirmarSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarSenha'],
})





export type LoginFormData = z.infer<typeof loginSchema>
export type RegistrarIgrejaFormData1 = z.infer<typeof registrarIgrejaSchema1>
export type RegistrarIgrejaFormData2 = z.infer<typeof registrarIgrejaSchema2>