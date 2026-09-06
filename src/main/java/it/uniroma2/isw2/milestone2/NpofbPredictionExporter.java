package it.uniroma2.isw2.milestone2;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Milestone 2 - esporta le predizioni out-of-fold richieste da ACUME per
 * calcolare NPofB20. Non sostituisce l'Experimenter: riusa le stesse dodici
 * configurazioni per produrre, per ogni ripetizione, una riga per ciascuna
 * coppia classe-release con ID, LOC, P(yes) e label reale.
 */
public final class NpofbPredictionExporter {

    private static final Logger LOGGER =
            Logger.getLogger(NpofbPredictionExporter.class.getName());

    private static final Path MODEL_DATASET = Paths.get("dataset_tajo_m2.arff");
    private static final Path METADATA_DATASET = Paths.get("dataset_tajo.csv");
    private static final Path OUTPUT_DIRECTORY = Paths.get("m2_predictions");

    private static final int FOLDS = 10;
    private static final int REPETITIONS = 10;
    private static final String YES = "yes";

    private NpofbPredictionExporter() {
    }

    public static void main(String[] args) throws Exception {
        Instances data = loadModelDataset();
        List<PredictionMetadata> metadata = loadMetadata(data);

        Files.createDirectories(OUTPUT_DIRECTORY);
        writeInstanceMapping(metadata);

        for (Map.Entry<String, Supplier<Classifier>> entry
                : ClassifierConfigurations.createAll().entrySet()) {
            exportConfiguration(entry.getKey(), entry.getValue(), data, metadata);
        }

        LOGGER.info("Predizioni ACUME scritte in "
                + OUTPUT_DIRECTORY.toAbsolutePath());
    }

    private static Instances loadModelDataset() throws Exception {
        Instances data = DataSource.read(MODEL_DATASET.toString());
        if (data == null || data.numInstances() == 0) {
            throw new IllegalStateException(
                    "Dataset ARFF vuoto o non leggibile: " + MODEL_DATASET);
        }
        data.setClassIndex(data.numAttributes() - 1);
        if (!data.classAttribute().isNominal()
                || data.classAttribute().indexOfValue(YES) < 0) {
            throw new IllegalStateException(
                    "L'attributo BUGGY deve contenere il valore 'yes'.");
        }
        return data;
    }

    private static List<PredictionMetadata> loadMetadata(Instances data)
            throws Exception {
        List<PredictionMetadata> metadata = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(
                METADATA_DATASET.toFile()))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalStateException("CSV metadata vuoto.");
            }
            int releaseIndex = findColumn(header, "RELEASE_ID");
            int classIndex = findColumn(header, "CLASS");
            int locIndex = findColumn(header, "LOC");
            int buggyIndex = findColumn(header, "BUGGY");

            String[] row;
            int id = 1;
            while ((row = reader.readNext()) != null) {
                String actual = row[buggyIndex].trim().toLowerCase(Locale.ROOT);
                metadata.add(new PredictionMetadata(
                        id++,
                        row[releaseIndex],
                        row[classIndex],
                        Integer.parseInt(row[locIndex]),
                        actual
                ));
            }
        }

        if (metadata.size() != data.numInstances()) {
            throw new IllegalStateException(
                    "Il CSV con metadata (" + metadata.size()
                            + " righe) non corrisponde all'ARFF ("
                            + data.numInstances() + " istanze).");
        }
        for (int i = 0; i < metadata.size(); i++) {
            String arffActual = data.instance(i).stringValue(data.classIndex());
            if (!metadata.get(i).actual().equalsIgnoreCase(arffActual)) {
                throw new IllegalStateException(
                        "Allineamento ARFF/CSV non valido alla riga " + (i + 1));
            }
        }
        return List.copyOf(metadata);
    }

    private static int findColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(header[i].trim())) {
                return i;
            }
        }
        throw new IllegalStateException("Colonna mancante nel CSV metadata: " + name);
    }

    private static void writeInstanceMapping(List<PredictionMetadata> metadata)
            throws IOException {
        Path mapping = OUTPUT_DIRECTORY.resolve("instances.csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(mapping.toFile()))) {
            writer.writeNext(new String[]{"ID", "RELEASE_ID", "CLASS", "LOC", "ACTUAL"});
            for (PredictionMetadata row : metadata) {
                writer.writeNext(new String[]{
                        String.valueOf(row.id()),
                        row.releaseId(),
                        row.classPath(),
                        String.valueOf(row.loc()),
                        row.actual().toUpperCase(Locale.ROOT)
                });
            }
        }
    }

    private static void exportConfiguration(
            String configuration,
            Supplier<Classifier> classifierSupplier,
            Instances originalData,
            List<PredictionMetadata> metadata) throws Exception {

        Path configurationDirectory = OUTPUT_DIRECTORY.resolve(configuration);
        Files.createDirectories(configurationDirectory);
        LOGGER.info(() -> "Esportazione predizioni: " + configuration);

        for (int run = 1; run <= REPETITIONS; run++) {
            Instances dataWithId = withInstanceId(originalData);
            dataWithId.randomize(new Random(run));
            dataWithId.stratify(FOLDS);

            Path output = configurationDirectory.resolve(
                    String.format(Locale.ROOT, "run-%02d.csv", run));
            exportRun(classifierSupplier, dataWithId, metadata, output);
        }
    }

    private static Instances withInstanceId(Instances originalData) {
        Instances copy = new Instances(originalData);
        copy.insertAttributeAt(new Attribute("INSTANCE_ID"), 0);
        copy.setClassIndex(copy.numAttributes() - 1);
        for (int i = 0; i < copy.numInstances(); i++) {
            copy.instance(i).setValue(0, i + 1);
        }
        return copy;
    }

    private static void exportRun(
            Supplier<Classifier> classifierSupplier,
            Instances dataWithId,
            List<PredictionMetadata> metadata,
            Path output) throws Exception {

        // ACUME legge le righe con String.split(",") e non gestisce le virgolette CSV.
        // Le quattro colonne di questo file sono prive di virgole, quindi si puo' omettere
        // il quoting mantenendo un formato semplice e compatibile con il tool.
        try (CSVWriter writer = new CSVWriter(
                new FileWriter(output.toFile()),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.NO_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {
            writer.writeNext(new String[]{"ID", "SIZE", "PREDICTED", "ACTUAL"});

            for (int fold = 0; fold < FOLDS; fold++) {
                Instances trainWithId = dataWithId.trainCV(FOLDS, fold);
                Instances testWithId = dataWithId.testCV(FOLDS, fold);
                Instances train = withoutInstanceId(trainWithId);
                Instances test = withoutInstanceId(testWithId);

                Classifier classifier = classifierSupplier.get();
                classifier.buildClassifier(train);
                writeFoldPredictions(writer, classifier, testWithId, test, metadata);
            }
        }
    }

    private static Instances withoutInstanceId(Instances dataWithId) {
        Instances copy = new Instances(dataWithId);
        copy.deleteAttributeAt(0);
        copy.setClassIndex(copy.numAttributes() - 1);
        return copy;
    }

    private static void writeFoldPredictions(
            CSVWriter writer,
            Classifier classifier,
            Instances testWithId,
            Instances test,
            List<PredictionMetadata> metadata) throws Exception {

        int yesIndex = test.classAttribute().indexOfValue(YES);
        for (int i = 0; i < test.numInstances(); i++) {
            int metadataIndex = (int) testWithId.instance(i).value(0) - 1;
            PredictionMetadata row = metadata.get(metadataIndex);
            String actual = test.instance(i).stringValue(test.classIndex());
            if (!row.actual().equalsIgnoreCase(actual)) {
                throw new IllegalStateException(
                        "Label non coerente per l'istanza " + row.id());
            }

            double probabilityYes = classifier.distributionForInstance(test.instance(i))[yesIndex];
            writer.writeNext(new String[]{
                    String.valueOf(row.id()),
                    String.valueOf(row.loc()),
                    String.format(Locale.US, "%.12f", probabilityYes),
                    actual.toUpperCase(Locale.ROOT)
            });
        }
    }
}
