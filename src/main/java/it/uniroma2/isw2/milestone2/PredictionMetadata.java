package it.uniroma2.isw2.milestone2;

/**
 * Informazioni non predittive mantenute fuori dal modello, ma necessarie per
 * collegare una predizione alla classe-release e calcolare metriche effort-aware.
 */
record PredictionMetadata(
        int id,
        String releaseId,
        String classPath,
        int loc,
        String actual) {
}
