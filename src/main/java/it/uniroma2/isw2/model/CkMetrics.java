package it.uniroma2.isw2.model;

/**
 * Metriche di prodotto (famiglia Chidamber &amp; Kemerer) calcolate dal tool CK
 * sullo snapshot sorgente di una classe in una release.
 *
 * @param wmc       Weighted Methods per Class
 * @param dit       Depth of Inheritance Tree
 * @param rfc       Response For a Class
 * @param fanIn     numero di classi che dipendono dalla classe (afferent)
 * @param fanOut    numero di classi da cui la classe dipende (efferent)
 * @param nMethods  numero di metodi della classe (stima di complessità/dimensione)
 */
public record CkMetrics(
        int wmc,
        int dit,
        int rfc,
        int fanIn,
        int fanOut,
        int nMethods) {

    private static final CkMetrics ZERO = new CkMetrics(0, 0, 0, 0, 0, 0);

    /** Metriche nulle, per classi non trovate da CK. */
    public static CkMetrics zero() {
        return ZERO;
    }
}
