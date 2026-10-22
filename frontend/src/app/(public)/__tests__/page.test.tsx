import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import LandingPage from '../page';

// Mock de Image do Next.js
vi.mock('next/image', () => ({
    default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={alt} {...props} />
    )
}));

// Mock do motion/react para testes estáticos e rápidos
vi.mock('motion/react', () => ({
    motion: {
        div: ({ children, ...props }: { children?: React.ReactNode; [key: string]: unknown }) => <div {...props}>{children}</div>
    },
    AnimatePresence: ({ children }: { children?: React.ReactNode }) => <>{children}</>
}));

// Mock do servico de planos para teste resiliente
vi.mock('@/services/plano.service', () => ({
    buscarPlanosServidor: vi.fn().mockResolvedValue([])
}));

describe('LandingPage Comercial 2026', () => {
    it('deve renderizar o Hero com a marca Domus e a headline editorial', () => {
        render(<LandingPage />);
        expect(screen.getAllByText(/Domus/i)[0]).toBeInTheDocument();
        expect(screen.getByText(/tecnologia a serviço da fé/i)).toBeInTheDocument();
        expect(screen.getByText(/SISTEMA DE GESTÃO PARA IGREJAS/i)).toBeInTheDocument();
    });

    it('deve renderizar os 4 cards de planos com precos mensais', () => {
        render(<LandingPage />);
        expect(screen.getByRole('heading', { name: 'Básico' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Pro' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Pro+' })).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Enterprise' })).toBeInTheDocument();
        expect(screen.getByText('49')).toBeInTheDocument();
        expect(screen.getByText('129')).toBeInTheDocument();
    });

    it('deve alternar o conteudo dos 6 modulos ao clicar nas abas', () => {
        render(<LandingPage />);
        expect(screen.getByText(/Ficha cadastral completa, cargos ministeriais e contatos/i)).toBeInTheDocument();

        // Clica na aba de celulas
        const tabCelulas = screen.getByRole('tab', { name: /Células & Grupos/i });
        fireEvent.click(tabCelulas);

        expect(screen.getByText(/Gestão de pequenas comunidades, reuniões e anfitriões/i)).toBeInTheDocument();
    });

    it('deve expandir e recolher perguntas no FAQ sanfona', () => {
        render(<LandingPage />);
        const perguntaBtn = screen.getByText(/Preciso cadastrar cartão de crédito para iniciar o teste\?/i);
        
        // Clica para abrir
        fireEvent.click(perguntaBtn);
        expect(screen.getByText(/O cartão de crédito é cadastrado no início para ativar seus 14 dias de teste/i)).toBeInTheDocument();

        // Clica novamente para fechar
        fireEvent.click(perguntaBtn);
        expect(screen.queryByText(/O cartão de crédito é cadastrado no início para ativar seus 14 dias de teste/i)).not.toBeInTheDocument();
    });

    it('deve direcionar os botoes de comecar gratis para /planos?origem=trial e ter link do WhatsApp do consultor', () => {
        render(<LandingPage />);
        
        const linkTrialHeader = screen.getByRole('link', { name: /Começar grátis/i });
        expect(linkTrialHeader).toHaveAttribute('href', '/planos?origem=trial');

        const linkWhats = screen.getByRole('link', { name: /Falar com consultor/i });
        expect(linkWhats).toHaveAttribute('href', expect.stringContaining('5583994023173'));

        expect(screen.getByRole('link', { name: /Termos de Uso/i })).toHaveAttribute('href', '/termos');
        expect(screen.getByRole('link', { name: /Privacidade/i })).toHaveAttribute('href', '/privacidade');
        expect(screen.getByRole('link', { name: /Segurança & LGPD/i })).toHaveAttribute('href', '/seguranca');
        expect(screen.getByText(/© 2026 Domus Tecnologia Eclesial/i)).toBeInTheDocument();
    });
});
