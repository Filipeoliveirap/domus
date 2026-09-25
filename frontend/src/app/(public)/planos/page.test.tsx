import { render, screen } from '@testing-library/react';
import PlanosPage from './page';

describe('PlanosPage', () => {
    it('deve renderizar os 4 planos com seus respectivos valores', () => {
        render(<PlanosPage />);
        expect(screen.getByText('Básico')).toBeInTheDocument();
        expect(screen.getByText('Pro')).toBeInTheDocument();
        expect(screen.getByText('Pro+')).toBeInTheDocument();
        expect(screen.getByText('Enterprise')).toBeInTheDocument();
        expect(screen.getByText(/79,00/)).toBeInTheDocument();
        expect(screen.getByText(/179,00/)).toBeInTheDocument();
    });
});
