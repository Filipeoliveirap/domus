import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import PlanosPage from '../page';

// Mock do servico de planos para teste resiliente
vi.mock('@/services/plano.service', () => ({
    buscarPlanosServidor: vi.fn().mockResolvedValue([])
}));

describe('PlanosPage', () => {
    it('deve renderizar o cabecalho padrao quando nao vem de trial', () => {
        render(<PlanosPage />);
        expect(screen.getByText('Escolha o plano ideal para sua igreja')).toBeInTheDocument();
        expect(screen.getByText(/Cadastre sua igreja e teste por 14 dias sem cobrança imediata/i)).toBeInTheDocument();
    });
});
