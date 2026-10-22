'use client'

import { useQuery } from '@tanstack/react-query'
import { obterResumoAssinatura } from '@/services/assinatura.service'

export function usePlanoFeatures() {
    const temFeature = (_feature?: string): boolean => {
        // Todas as funcionalidades liberadas em qualquer plano do Domus
        return true;
    };

    return {
        temFeature,
    };
}
