import React from 'react';
import Link from 'next/link';
import { Lock } from 'lucide-react';
import { usePlanoFeatures } from '@/hooks/usePlanoFeatures';
import { useAuthStore } from '@/store/authStore';
import styles from './BannerUpgradePlano.module.css';

interface FeatureGuardProps {
    feature: string;
    featuresHabilitadas?: string[];
    fallback?: React.ReactNode;
    children: React.ReactNode;
}

export function BannerUpgradePlano({ featureNome }: { featureNome?: string }) {
    const role = useAuthStore((s) => s.role);
    const ehAdmin = role === 'ADMIN_IGREJA';

    return (
        <div className={styles.bannerContainer}>
            <div className={styles.bannerIcon}>
                <Lock size={20} />
            </div>
            <div>
                <h4 className={styles.bannerTitle}>Funcionalidade Exclusiva do Plano Pro</h4>
                <p className={styles.bannerText}>
                    {featureNome ? `O recurso '${featureNome}'` : 'Esta funcionalidade'} não está incluída no seu plano atual (Básico).
                </p>
                {!ehAdmin && (
                    <p className={styles.bannerText} style={{ marginTop: '4px', fontSize: '0.85rem', color: '#64748b' }}>
                        Entre em contato com o administrador da sua igreja para habilitar este recurso.
                    </p>
                )}
            </div>
            {ehAdmin && (
                <Link href="/configuracoes/assinatura" className={styles.btnUpgrade}>
                    Fazer Upgrade do Plano
                </Link>
            )}
        </div>
    );
}

export default function FeatureGuard({
    feature,
    featuresHabilitadas = [],
    fallback,
    children
}: FeatureGuardProps) {
    const { temFeature } = usePlanoFeatures();

    if (!temFeature(feature)) {
        return <>{fallback || <BannerUpgradePlano featureNome={feature} />}</>;
    }

    return <>{children}</>;
}
