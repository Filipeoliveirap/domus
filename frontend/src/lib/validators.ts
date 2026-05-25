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
  telefoneContato: z.string().trim().optional(),
  
})

export const registrarIgrejaSchema2 = z.object({
    nomeAdmin: z.string().trim().min(1, 'Nome do administrador é obrigatório'),
    emailAdmin: z.string().trim().min(1, 'E-mail do administrador é obrigatório').email('E-mail inválido'),
    senhaAdmin: z.string().min(8, 'A senha deve conter no mínimo 8 caracteres'),
})





export type LoginFormData = z.infer<typeof loginSchema>
export type RegistrarIgrejaFormData1 = z.infer<typeof registrarIgrejaSchema1>
export type RegistrarIgrejaFormData2 = z.infer<typeof registrarIgrejaSchema2>