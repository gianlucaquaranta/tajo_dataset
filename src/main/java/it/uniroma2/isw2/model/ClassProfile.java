package it.uniroma2.isw2.model;

import java.lang.reflect.Modifier;

/**
 * Profilo di una classe prodotto da CK: le metriche di prodotto piu' le
 * informazioni sul <i>tipo</i> di dichiarazione (class/interface/enum) e i
 * relativi modificatori. Serve, oltre che per le metriche, a classificare la
 * classe (es. per escludere enum, interfacce e classi astratte dai ranking).
 *
 * @param metrics   metriche C&amp;K (WMC, DIT, RFC, Fan-In, Fan-Out, NMethods)
 * @param type      tipo CK: "class", "interface", "enum", ...
 * @param modifiers modificatori (stile {@link java.lang.reflect.Modifier})
 */
public record ClassProfile(CkMetrics metrics, String type, int modifiers) {

    public boolean isEnum() {
        return "enum".equalsIgnoreCase(type);
    }

    public boolean isInterface() {
        return "interface".equalsIgnoreCase(type);
    }

    /** true se e' una classe (non interfaccia/enum) dichiarata abstract. */
    public boolean isAbstractClass() {
        return "class".equalsIgnoreCase(type) && Modifier.isAbstract(modifiers);
    }
}
