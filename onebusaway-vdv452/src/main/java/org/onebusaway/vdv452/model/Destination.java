/**
 * Extension to extract headsigns from VDV452 and provide them in GTFS trips.txt.
 * All files changed for that:
 *  onebusaway-vdv452-to-gtfs-converter/src/main/java/org/onebusaway/vdv452/Vdv452ToGtfsFactory.java
 *  onebusaway-vdv452/src/main/java/org/onebusaway/vdv452/Vdv452Dao.java
 *  onebusaway-vdv452/src/main/java/org/onebusaway/vdv452/Vdv452Reader.java
 *  onebusaway-vdv452/src/main/java/org/onebusaway/vdv452/model/Destination.java
 *  onebusaway-vdv452/src/main/java/org/onebusaway/vdv452/model/RouteSequence.java
 *  onebusaway-vdv452/src/main/java/org/onebusaway/vdv452/serialization/EntityFieldMappingFactory.java
 */
package org.onebusaway.vdv452.model;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.vdv452.serialization.VersionedIdFieldMappingFactory;

@CsvFields(filename = "REC_ZNR.x10")
public class Destination extends IdentityBean<VersionedId> {

    private static final long serialVersionUID = 1L;

    @CsvField(name = "ZNR_NR", mapping = VersionedIdFieldMappingFactory.class)
    private VersionedId id;

    @CsvField(name = "HECKANZEIGETEXT", optional = true)
    private String headsign;

    @Override
    public VersionedId getId() {
        return id;
    }

    @Override
    public void setId(VersionedId id) {
        this.id = id;
    }

    public String getHeadsign() {
        return headsign;
    }

    public void setHeadsign(String headsign) {
        // Handle "Text     \n    Text2" etc
        headsign = headsign.replace("\\n", " ");
        headsign = headsign.trim().replaceAll(" +", " ");
        this.headsign = headsign;
    }
}