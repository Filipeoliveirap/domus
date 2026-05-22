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

export type LoginFormData = z.infer<typeof loginSchema>