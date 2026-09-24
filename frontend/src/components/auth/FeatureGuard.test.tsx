import { render, screen } from '@testing-library/react';
import FeatureGuard from './FeatureGuard';

describe('FeatureGuard', () => {
    it('deve ocultar conteudo quando a feature nao for permitida', () => {
        render(
            <FeatureGuard feature="FEED_SOCIAL" featuresHabilitadas={[]}>
                <div>Conteudo Privado</div>
            </FeatureGuard>
        );
        expect(screen.queryByText('Conteudo Privado')).not.toBeInTheDocument();
    });

    it('deve exibir conteudo quando a feature for permitida', () => {
        render(
            <FeatureGuard feature="FEED_SOCIAL" featuresHabilitadas={['FEED_SOCIAL']}>
                <div>Conteudo Privado</div>
            </FeatureGuard>
        );
        expect(screen.getByText('Conteudo Privado')).toBeInTheDocument();
    });
});
