package it.uniroma2.isw2.model;

import java.util.Locale;

/** Una riga del dataset, indipendente dal formato con cui verra' serializzata. */
public record DatasetRow(
        String project,
        int releaseId,
        String releaseName,
        String classPath,
        int loc,
        int nSmells,
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
        int minExp,
        int nMethods,
        int dit,
        int rfc,
        int fanIn,
        int fanOut,
        int cc,
        boolean buggy) {

    public String[] toCsvColumns() {
        return new String[] {
                project,
                String.valueOf(releaseId),
                releaseName,
                classPath,
                String.valueOf(loc),
                String.valueOf(nSmells),
                String.valueOf(locAdded),
                String.valueOf(maxLocAdded),
                String.valueOf(churn),
                formatDecimal(avgChurn),
                String.valueOf(nr),
                String.valueOf(nFix),
                formatDecimal(fixRate),
                String.valueOf(nAuth),
                formatDecimal(avgChangeSet),
                String.valueOf(maxChangeSet),
                formatDecimal(avgNd),
                String.valueOf(ageLastChange),
                String.valueOf(minExp),
                String.valueOf(nMethods),
                String.valueOf(dit),
                String.valueOf(rfc),
                String.valueOf(fanIn),
                String.valueOf(fanOut),
                String.valueOf(cc),
                buggy ? "yes" : "no"
        };
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
