import { api } from '@/lib/api'

export interface PlanoServidor {
    id: string;
    nomeExibicao: string;
    limitePessoas: number;
    limiteCongregacoes: number;
    valorMensal: number;
    featuresHabilitadas: string[];
    descricoesFeaturesHabilitadas: string[];
}

export interface CadastroCongregacaoPayload {
    codigoConvite: string;
    nome: string;
    nomeAdmin: string;
    emailAdmin: string;
    senha: string;
}

export async function registrarCongregacaoFilha(payload: CadastroCongregacaoPayload): Promise<any> {
    const response = await api.post('/igrejas/registrar-congregacao', payload);
    return response.data;
}

export async function consultarCodigoConvite(codigo: string): Promise<any> {
    const response = await api.get(`/igrejas/convites/${encodeURIComponent(codigo)}`);
    return response.data;
}

export async function buscarPlanosServidor(): Promise<PlanoServidor[]> {
    try {
        const response = await api.get<PlanoServidor[]>('/api/planos');
        if (Array.isArray(response.data)) {
            return response.data;
        }
        throw new Error('Resposta de planos não é um array válido');
    } catch {
        // Fallback defensivo sincronizado com PlanoAssinatura v2
        return [
            {
                id: 'BASICO',
                nomeExibicao: 'Básico',
                limitePessoas: 50,
                limiteCongregacoes: 0,
                valorMensal: 49,
                featuresHabilitadas: ['FEED_SOCIAL', 'CONTAS_A_PAGAR', 'CHECKOUT_EVENTO', 'RELATORIOS_AVANCADOS', 'CAMPOS_PERSONALIZADOS'],
                descricoesFeaturesHabilitadas: [
                    'Todas as funcionalidades liberadas',
                    'Membros, Células & Famílias',
                    'Contas a Pagar & Dízimos',
                    'Gestão de Cultos & Eventos',
                    'Balancete Anual Contábil',
                    'Feed Social da Comunidade'
                ]
            },
            {
                id: 'PRO',
                nomeExibicao: 'Pro',
                limitePessoas: 200,
                limiteCongregacoes: 2,
                valorMensal: 129,
                featuresHabilitadas: ['FEED_SOCIAL', 'CONTAS_A_PAGAR', 'CHECKOUT_EVENTO', 'RELATORIOS_AVANCADOS', 'CAMPOS_PERSONALIZADOS', 'COMPARTILHAMENTO_CONGREGACOES'],
                descricoesFeaturesHabilitadas: [
                    'Todas as funcionalidades liberadas',
                    'Até 200 pessoas ativas',
                    'Até 2 congregações filhas inclusas',
                    'Contas a Pagar & Dízimos',
                    'Cobrança de Eventos (PIX/Cartão)',
                    'Balancete Anual (Próprio & Consolidado)',
                    'Campos Personalizados'
                ]
            },
            {
                id: 'PRO_PLUS',
                nomeExibicao: 'Pro+',
                limitePessoas: 600,
                limiteCongregacoes: 5,
                valorMensal: 249,
                featuresHabilitadas: ['FEED_SOCIAL', 'CONTAS_A_PAGAR', 'CHECKOUT_EVENTO', 'RELATORIOS_AVANCADOS', 'CAMPOS_PERSONALIZADOS', 'COMPARTILHAMENTO_CONGREGACOES'],
                descricoesFeaturesHabilitadas: [
                    'Todas as funcionalidades liberadas',
                    'Até 600 pessoas ativas',
                    'Até 5 congregações filhas inclusas',
                    'Gestão multi-igreja integrada',
                    'Relatórios Contábeis Avançados',
                    'Suporte prioritário'
                ]
            },
            {
                id: 'ENTERPRISE',
                nomeExibicao: 'Enterprise',
                limitePessoas: 99999,
                limiteCongregacoes: 9999,
                valorMensal: 449,
                featuresHabilitadas: ['FEED_SOCIAL', 'CONTAS_A_PAGAR', 'CHECKOUT_EVENTO', 'RELATORIOS_AVANCADOS', 'CAMPOS_PERSONALIZADOS', 'COMPARTILHAMENTO_CONGREGACOES'],
                descricoesFeaturesHabilitadas: [
                    'Todas as funcionalidades liberadas',
                    'Pessoas e membros ilimitados',
                    'Congregações e filiais ilimitadas',
                    'Balancetes Consolidados e Matriz',
                    'Cobrança de Eventos com Taxas Otimizadas',
                    'Treinamento & Onboarding dedicado',
                    'Gerente de conta e SLA garantido'
                ]
            }
        ];
    }
}
