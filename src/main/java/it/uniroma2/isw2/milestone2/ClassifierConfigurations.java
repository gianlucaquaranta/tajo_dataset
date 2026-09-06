package it.uniroma2.isw2.milestone2;

import weka.attributeSelection.BestFirst;
import weka.attributeSelection.CfsSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.WekaPackageManager;
import weka.filters.Filter;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Factory delle dodici configurazioni reali valutate nella Milestone 2. */
final class ClassifierConfigurations {

    private static final String SMOTE_CLASS =
            "weka.filters.supervised.instance.SMOTE";

    private ClassifierConfigurations() {
    }

    static Map<String, Supplier<Classifier>> createAll() {
        Map<String, Supplier<Classifier>> configurations = new LinkedHashMap<>();

        configurations.put("random-forest", ClassifierConfigurations::randomForest);
        configurations.put("naive-bayes", NaiveBayes::new);
        configurations.put("ibk", ClassifierConfigurations::ibk);

        configurations.put("random-forest-fs",
                () -> withFeatureSelection(randomForest()));
        configurations.put("naive-bayes-fs",
                () -> withFeatureSelection(new NaiveBayes()));
        configurations.put("ibk-fs",
                () -> withFeatureSelection(ibk()));

        configurations.put("random-forest-smote",
                () -> withSmote(randomForest()));
        configurations.put("naive-bayes-smote",
                () -> withSmote(new NaiveBayes()));
        configurations.put("ibk-smote",
                () -> withSmote(ibk()));

        configurations.put("random-forest-smote-fs",
                () -> withSmote(withFeatureSelection(randomForest())));
        configurations.put("naive-bayes-smote-fs",
                () -> withSmote(withFeatureSelection(new NaiveBayes())));
        configurations.put("ibk-smote-fs",
                () -> withSmote(withFeatureSelection(ibk())));

        return configurations;
    }

    private static RandomForest randomForest() {
        RandomForest classifier = new RandomForest();
        classifier.setNumIterations(100);
        classifier.setNumExecutionSlots(1);
        classifier.setSeed(1);
        return classifier;
    }

    private static IBk ibk() {
        return new IBk(1);
    }

    private static AttributeSelectedClassifier withFeatureSelection(
            Classifier classifier) {
        AttributeSelectedClassifier selected = new AttributeSelectedClassifier();
        selected.setEvaluator(new CfsSubsetEval());

        BestFirst search = new BestFirst();
        try {
            search.setSearchTermination(5);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Impossibile configurare la ricerca BestFirst.", e);
        }
        selected.setSearch(search);
        selected.setClassifier(classifier);
        return selected;
    }

    private static FilteredClassifier withSmote(Classifier classifier) {
        FilteredClassifier filtered = new FilteredClassifier();
        filtered.setSeed(1);
        filtered.setFilter(createSmote());
        filtered.setClassifier(classifier);
        return filtered;
    }

    /**
     * SMOTE e' un package Weka esterno. Il caricamento riflessivo evita di
     * legare il progetto a un JAR locale: al runtime Weka lo carica dal Package
     * Manager, come avviene nell'interfaccia grafica usata per la Milestone 2.
     */
    private static Filter createSmote() {
        try {
            Class<?> smoteClass = loadSmoteClass();
            Filter smote = (Filter) smoteClass.getDeclaredConstructor()
                    .newInstance();
            smote.setOptions(new String[]{
                    "-C", "2",
                    "-K", "5",
                    "-P", "202.0",
                    "-S", "1"
            });
            return smote;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Il package Weka SMOTE non e' installato. "
                            + "Installalo dal Package Manager di Weka.", e);
        } catch (InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Impossibile inizializzare il filtro SMOTE.", e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Impossibile configurare il filtro SMOTE.", e);
        }
    }

    private static Class<?> loadSmoteClass() throws Exception {
        try {
            WekaPackageManager.loadPackages(false);
            return Class.forName(SMOTE_CLASS);
        } catch (ClassNotFoundException ignored) {
            Path packageJar = smotePackageJar();
            if (!Files.isRegularFile(packageJar)) {
                throw new ClassNotFoundException(
                        "JAR SMOTE non trovato in " + packageJar);
            }
            URL packageUrl = packageJar.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{packageUrl},
                    ClassifierConfigurations.class.getClassLoader());
            return Class.forName(SMOTE_CLASS, true, loader);
        }
    }

    private static Path smotePackageJar() {
        String configuredJar = System.getProperty("weka.smote.jar");
        if (configuredJar != null && !configuredJar.isBlank()) {
            return Paths.get(configuredJar);
        }
        return Paths.get(System.getProperty("user.home"), "wekafiles",
                "packages", "SMOTE", "SMOTE.jar");
    }
}
