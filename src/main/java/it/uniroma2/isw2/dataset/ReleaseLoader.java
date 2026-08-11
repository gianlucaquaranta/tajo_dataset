package it.uniroma2.isw2.dataset;

import it.uniroma2.isw2.model.Release;

import java.io.BufferedReader;
import java.io.FileReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReleaseLoader {

    private ReleaseLoader(){}

    public static List<Release> load(String csvPath)
            throws Exception {

        List<Release> releases = new ArrayList<>();

        try (BufferedReader br =
                     new BufferedReader(
                             new FileReader(csvPath))) {

            br.readLine();

            String line;

            while((line = br.readLine()) != null) {

                String[] parts = line.split(",");

                int releaseId =
                        Integer.parseInt(parts[0]);

                String releaseName =
                        parts[2];

                LocalDateTime date =
                        LocalDateTime.parse(parts[3]);

                releases.add(
                        new Release(
                                releaseId,
                                releaseName,
                                date
                        )
                );
            }
        }

        return releases;
    }

    public static List<Release> keepFirst34Percent(
            List<Release> releases) {

        int amount =
                (int)Math.floor(
                        releases.size() * 0.34
                );

        return releases.subList(0, amount);
    }
}