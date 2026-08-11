package it.uniroma2.isw2.model;

import java.time.LocalDateTime;

public class Release {

    private final int releaseId;
    private final String releaseName;
    private final LocalDateTime releaseDate;

    public Release(int releaseId,
                   String releaseName,
                   LocalDateTime releaseDate) {

        this.releaseId = releaseId;
        this.releaseName = releaseName;
        this.releaseDate = releaseDate;
    }

    public int getReleaseId() {
        return releaseId;
    }

    public String getReleaseName() {
        return releaseName;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }
}
