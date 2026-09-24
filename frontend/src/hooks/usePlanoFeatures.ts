export function usePlanoFeatures(featuresHabilitadas: string[] = []) {
    const temFeature = (feature: string): boolean => {
        return featuresHabilitadas.includes(feature);
    };

    return { temFeature };
}
