import React from 'react';
import Link from 'next/link';
import { usePlanoFeatures } from '@/hooks/usePlanoFeatures';
import styles from './BannerUpgradePlano.module.css';

interface FeatureGuardProps {
    feature: string;
    featuresHabilitadas?: string[];
    fallback?: React.ReactNode;
    children: React.ReactNode;
}

export function BannerUpgradePlano({ featureNome }: { featureNome?: string }) {
    return (
        <div className={styles.bannerContainer}>
            <div className={styles.bannerIcon}>🔒</div>
            <div>
                <h4 className={styles.bannerTitle}>Funcionalidade Exclusiva do Plano Pro</h4>
                <p className={styles.bannerText}>
                    {featureNome ? `O recurso '${featureNome}'` : 'Esta funcionalidade'} não está incluída no seu plano atual (Básico).
                </p>
            </div>
            <Link href="/configuracoes/assinatura" className={styles.btnUpgrade}>
                Fazer Upgrade do Plano
            </Link>
        </div>
    );
}

export default function FeatureGuard({
    feature,
    featuresHabilitadas = [],
    fallback,
    children
}: FeatureGuardProps) {
    const { temFeature } = usePlanoFeatures(featuresHabilitadas);

    if (!temFeature(feature)) {
        return <>{fallback || <BannerUpgradePlano featureNome={feature} />}</>;
    }

    return <>{children}</>;
}
