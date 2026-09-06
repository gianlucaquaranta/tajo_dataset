package it.uniroma2.isw2.utility;

import com.opencsv.CSVWriter;
import it.uniroma2.isw2.model.DatasetRow;

import java.io.FileWriter;
import java.util.List;

/** Utility di sola serializzazione del dataset in formato CSV. */
public final class CsvUtils {

    private CsvUtils() {
    }

    public static void writeRows(CSVWriter writer, List<DatasetRow> rows) {
        for (DatasetRow row : rows) {
            writer.writeNext(row.toCsvColumns());
        }
    }

    public static CSVWriter createWriter(String outputPath) throws Exception {
        CSVWriter writer = new CSVWriter(new FileWriter(outputPath));
        writer.writeNext(new String[] {
                "PROJECT", "RELEASE_ID", "RELEASE_NAME", "CLASS", "LOC",
                "NSMELLS", "LOC_ADDED", "MAX_LOC_ADDED", "CHURN", "AVG_CHURN",
                "NR", "NFIX", "FIX_RATE", "NAUTH", "AVG_CHANGE_SET",
                "MAX_CHANGE_SET", "AVG_ND", "AGE_LAST_CHANGE", "MIN_EXP",
                "NUM_METHODS", "DIT", "RFC", "FAN_IN", "FAN_OUT", "CC", "BUGGY"
        });
        return writer;
    }
}
