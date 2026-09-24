import React from 'react';
import { usePlanoFeatures } from '@/hooks/usePlanoFeatures';

interface FeatureGuardProps {
    feature: string;
    featuresHabilitadas?: string[];
    fallback?: React.ReactNode;
    children: React.ReactNode;
}

export default function FeatureGuard({
    feature,
    featuresHabilitadas = [],
    fallback = null,
    children
}: FeatureGuardProps) {
    const { temFeature } = usePlanoFeatures(featuresHabilitadas);

    if (!temFeature(feature)) {
        return <>{fallback}</>;
    }

    return <>{children}</>;
}
