package it.uniroma2.isw2.metrics;

import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.ast.ASTTypeDeclaration;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.metrics.JavaMetrics;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRulechainRule;
import net.sourceforge.pmd.lang.metrics.MetricsUtil;

/**
 * Regola PMD che calcola, per ogni classe top-level, la complessità ciclomatica
 * totale (metrica {@code WEIGHED_METHOD_COUNT} = somma delle CYCLO dei metodi) e
 * la comunica a {@link CcAnalyzer}. Non riporta violazioni: e' solo un
 * collettore di metriche eseguito dentro il normale batch PMD.
 */
public class CyclomaticComplexityRule extends AbstractJavaRulechainRule {

    public CyclomaticComplexityRule() {
        super(ASTTypeDeclaration.class);
        setName("CyclomaticComplexityCollector");
        setMessage("cc");
        setLanguage(LanguageRegistry.PMD.getLanguageById("java"));
    }

    @Override
    public Object visitJavaNode(JavaNode node, Object data) {
        if (node instanceof ASTTypeDeclaration type && type.isTopLevel()) {
            try {
                Integer cc = MetricsUtil.computeMetric(
                        JavaMetrics.WEIGHED_METHOD_COUNT, type);
                if (cc != null) {
                    CcAnalyzer.record(
                            type.getTextDocument().getFileId().getAbsolutePath(),
                            type.getSimpleName(),
                            cc);
                }
            } catch (RuntimeException ignored) {
                // metrica non supportata per questo tipo: ignora
            }
        }
        return data;
    }
}
