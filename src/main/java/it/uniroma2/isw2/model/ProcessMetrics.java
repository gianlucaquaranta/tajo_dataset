package it.uniroma2.isw2.model;

/**
 * Metriche di processo aggregate per una coppia (classe, release), calcolate
 * cumulativamente su tutta la storia della classe fino alla data della release.
 * <p>
 * Il record viene esteso man mano che si aggiungono gli altri gruppi.
 *
 * @param locAdded    somma delle linee aggiunte nella storia della classe
 * @param maxLocAdded massimo di linee aggiunte in una singola revisione
 * @param churn       somma di linee aggiunte + eliminate nella storia
 * @param avgChurn    churn medio per revisione (churn / numero di revisioni)
 * @param nr            numero di revisioni che hanno modificato la classe
 * @param nFix          numero di revisioni di bug fixing
 * @param fixRate       NFix / NR
 * @param nAuth         numero di autori distinti che hanno modificato la classe
 * @param avgChangeSet  media della change set size dei commit che toccano la classe
 * @param maxChangeSet  massima change set size tra quei commit
 * @param avgNd         media del numero di directory (ND) di quei commit
 * @param ageLastChange giorni tra l'ultima modifica della classe e la release
 * @param minExp        minima esperienza (n. commit nel progetto) tra gli autori
 */
public record ProcessMetrics(
        int locAdded,
        int maxLocAdded,
        int churn,
        double avgChurn,
        int nr,
        int nFix,
        double fixRate,
        int nAuth,
        double avgChangeSet,
        int maxChangeSet,
        double avgNd,
        int ageLastChange,
        int minExp) {

    private static final ProcessMetrics ZERO =
            new ProcessMetrics(0, 0, 0, 0.0, 0, 0, 0.0, 0, 0.0, 0, 0.0, 0, 0);

    /** Metriche nulle, per classi senza storia rilevabile. */
    public static ProcessMetrics zero() {
        return ZERO;
    }
}
