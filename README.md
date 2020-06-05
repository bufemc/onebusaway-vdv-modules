## VDV452 to GTFS Converter

* This repository is based on onebusaway-vdv-modules: https://github.com/OneBusAway/onebusaway-vdv-modules
* Reason: VDV452 data was delivered for a region, but couldn't be converted without modifications
* Working example would be the vdv452 data from: http://sasabus.org/de/opendata - http://open.sasabz.it/files/vdv.zip
* Documentation for vdv452 is added, but removed for public repository

### Usage
* Provide two folders, one with the (unzipped) vdv452 and another (empty, content will be overwritten) folder for GTFS
* You can choose if stop_name is generated from column ORT_REF_ORT_NAME instead of ORT_NAME (default) in REC_ORT.x10 by
    adding this argument:
    - -preferRefName
* By command line:
    - java -jar onebusaway-vdv452-converter-cli.jar [-preferRefName] input_vdv_folder output_gtfs_folder
* In IntelliJ or Eclipse:
    - Main class: org.onebusaway.vdv452.Vdv452ToGtfsConverterMain
    - Program arguments: [-preferRefName] input_vdv_folder output_gtfs_folder
    - Java SDK 1.8 tested (might work with earlier versions, too)

### Modifications (newest added at the end)

* Fixed: can handle also longitudes like 9.1234 now (previously had to be 09.1234)
* Changed: model/EStopType.java can also handle stop_type >= 3 now, but will just return new type SKIP, as these are 
    not relevant for GTFS (was meanwhile solved in original repository as well, so changes reverted here):

        "Bezeichner des funktionalen Typs eines Ortes <Ortstyp>
        1: Haltepunkt
        2: Betriebshofpunkt
        3: Ortsmarke
        4: LSA-Punkt
        5: Routenzwischenpunkt
        6: Betriebspunkt
        7: Grenzpunkt
        
* Changed: model/Stop.java allows ORT_NAME to be empty now: @CsvField(name = "ORT_NAME", optional = true)
* Changed: getStopTimesForJourney will skip entry if stop is null
* Changed: in main method locale forced to be US, to prevent coordinates are exported like "50,48","9,78"
    (that happens e.g. in Germany)
* Changed: coordinates with 0.0/0.0 are skipped for stops.txt AND stop_times.txt
* Changed: first coordinate is reference coordinate. If later coordinates from vdv452 are implausible 
    (see refMaxThreshold) we swap lon and lat. This was needed due to swapped coordinates in the 
    input data for "region", especially for an agency.
* Changed: only relevant stops (ONR_TYP_NR=1) are exported to stops.txt and stop_times.txt
    (that means other stops like depots (ONR_TYP_NR=2), border points etc. are not exported)
* Added: if available, zone_id (can also contain an id for a so called Wabe) is exported to stops.txt now, too
* Changed: Allow also illegal value "0" for lon/lat in REC_ORT.X10. Issue was raised for new data from agency
    an agency on 6th Sept 2017. Syntactially it is wrong (should be at least "00000000"), but would stop parsing and 
    generating GTFS otherwise, and happens only for irrelevant data (ONR_TYP_NR>=2).
* Changed: If there is no travel time on a trip at all (so say, first departure time is same as last departure)
    the trip is not saved to trips.txt and its stops is not saved to stop_times.txt. Reason were the trips for
    an agency in "region" with sometimes just 2 stops with same timing, and one even was the virtual bus arrival stop
    510099 at the Hbf/ROB.
* Changed: We don't count nor add stops with ONR_TYP_NR != 1 (e.g. 2 is a depot). If in the end the trip has
    1 stop only, the trip is not saved to trips.txt and its stops is not saved to stop_times.txt.
* Added: iff available, will retrieve the headsign via LID_VERLAUF.x10 (RouteSequence.java) and via 
    REC_ZNR.x10 (Destination.java) to set it for trips.txt in GTFS. In case of "region": iff there is a
    ZNR_NR (aka "destination number id") != 0 set in LID_VERLAUF, it seems to be set for first stop only,
    while all following stops of this line ZNR_NR is 0 ("Bezeichner der Zielanzeige <Zielnummer>. Die ZNR_NR 0 
    wird verwendet, um das Display zu löschen" aka: if following the standard, that would mean we have to
    remove the headsign?). For REC_ZNR.x10 and the column "HECKANZEIGETEXT" see attached file doc/VDV_0105.pdf or
    http://open.sasabz.it/files/VDV_0105.pdf at section 6.2.12 REC_ZNR.
* Added: by -preferRefName you can choose now if you prefer to use column ORT_REF_ORT_NAME instead of ORT_NAME
    in REC_ORT.x10 to build the stop_name for GTFS.
* Added: as for an agency we found trips with headsign "Leerfahrt" we found out that there are 4 trip types ("Fahrtarten"),
    and these ones using ZNR_NR 985 for "Leerfahrt" were mainly using trip type 3 or 4. So in conversion process
    we now only allow trip type 1 ("Normalfahrt"), while skipping others.
    FAHRTART_NR - these are the offical values and meanings:
    - 1: Normalfahrt
    - 2: Betriebshofausfahrt
    - 3: Betriebshofeinfahrt
    - 4: Zufahrt
* Added: While WaitTime.java - using ORT_HZTF.x10 - seems to be fully implemented and used,
    JourneyWaitTime.java - using REC_FRT_HZT.x10 - is implemented to grab all the data, but is not really used.
    Code has been extended to utilize JourneyWaitTime, too. To do so, it will first store the data in a list,
    split it up per journey/trip id, then add the waiting time in getStopTimesForJourney if there is one for
    this specific journey/trip and stop. See ADD_JWT in console output.
* Added: parsing of optional column LEISTUNGSART_NR in REC_FRT.x10, which is defined in MENGE_LEISTUNGSART.x10          
* Changed: DegreesMinutesSecondsFieldMappingFactory.java can also handle 'illegal' coordinate "1/1" now, which
    is like 0/0 above in the ocean, so we flag this as a no-coordinate. Happens only for (regarding ONR_TYP)
    uninteresting stops anyway.
   
### Assignment of vdv452 x10 files to model in org.onebusaway.vdv452.model

* BaseVersion.java => MENGE_BASIS_VERSIONEN.x10:
    - BASIS_VERSION
    - BASIS_VERSION_TEXT
* Block.java => REC_UMLAUF.x10
    - ANF_ORT
    - END_ORT
    - FZT_TYP_NR
* DayType.java => MENGE_TAGESART.x10
    - TAGESART_NR
    - TAGESART_TEXT
* Journey.java => REC_FRT.x10
    - FRT_FID
    - LI_NR => Line.java
    - FRT_START
    - FGR_NR
    - TAGESART_NR => DayType.java
    - FAHRTART_NR
    - LEISTUNGSART_NR => MENGE_LEISTUNGSART.x10
* JourneyWaitTime.java => REC_FRT_HZT.x10
    - FRT_FID => Journey.java
    - ORT_NR => Stop.java
    - FRT_HZT_ZEIT
* Line.java => REC_LID.x10
    - LI_NR
    - LI_KUERZEL
    - LIDNAME
* Period.java => FIRMENKALENDER.x10
    - BETRIEBSTAG
    - TAGESART_NR => DayType.java
    - BETRIEBSTAG_TEXT
* RouteSequence.java => LID_VERLAUF.x10
    - STR_LI_VAR
    - LI_LFD_NR
    - ORT_NR => Stop.java
    - ZNR_NR => REC_ZNR.x10
* Destination.java => REC_ZNR.x10
    - ZNR_NR
    - HECKANZEIGETEXT
* Stop.java => REC_ORT.x10
    - ZONE_WABE_NR
    - ORT_NR
    - ORT_NAME
    - ORT_POS_BREITE
    - ORT_POS_LAENGE
* StopPoint.java => REC_HP.x10
    - ORT_NR => Stop.java
* TimingGroup.java => MENGE_FGR.x10
    - FGR_NR
    - FGR_TEXT
* TransportCompany.java => ZUL_VERKEHRSBETRIEB.x10
    - UNTERNEHMEN
    - ABK_UNTERNEHMEN
    - BETRIEBSGEBIET_BEZ
* TravelTime.java => SEL_FZT_FELD.x10
    - FGR_NR => TimingGroup.java
    - ORT_NR
    - SEL_ZIEL
    - SEL_FZT
* VehicleType.java => MENGE_FZG_TYP.x10
    - FZG_TYP_NR
    - FZG_LAENGE
    - FZG_TYP_SITZ
    - FZG_TYP_STEH
    - SONDER_PLATZ
    - FZG_TYP_TEXT
    - STR_FZG_TYP
* WaitTime.java => ORT_HZTF.x10
    - FGR_NR => TimingGroup.java
    - ORT_NR => Stop.java
    - HP_HZT
    
    
onebusaway-vdv-modules [![Build Status](https://travis-ci.org/OneBusAway/onebusaway-vdv-modules.svg?branch=master)](https://travis-ci.org/OneBusAway/onebusaway-vdv-modules)
======================

Libraries and tools for working with transit data conforming to the VDV specification.  Includes:

* onebusaway-vdv452: a Java library for parsing and processing [VDV-452](http://mitglieder.vdv.de/module/layout_upload/452_sesv14.pdf) transit schedule data.
* onebusaway-vdv452-converter-cli: a Java command-line utility for converting VDV-452 schedule data into the [GTFS](https://developers.google.com/transit/gtfs/) format

## Converting VDV-452 to GTFS

To convert transit schedule data in the VDV-452 format into GTFS, use the our handy utility.

[Download the latest version of onebusaway-vdv452-converter-cli](http://nexus.onebusaway.org/service/local/artifact/maven/redirect?r=public&g=org.onebusaway&a=onebusaway-vdv452-to-gtfs-converter-cli&v=LATEST)

The utility is a executable Java jar file, so you'll need Java installed to run the tool.  To run it:

    java -jar onebusaway-vdv452-converter-cli.jar [-args] input_vdv_path output_gtfs_path


`javac` version 1.8.0_131 has worked on Debian testing, and version 1.8.0_111 has worked on Windows.

To compile your own `onebusaway-vdv452-converter-cli.jar`, for instance with own logic changes,
without using an IDE:
 
    # this works on Debian testing in June 2017, at least
    sudo apt install default-jdk maven 
 
    cd <path to gtfs-converter-vdv452 repo>
    mvn clean
    mvn package
    cd onebusaway-vdv452-to-gtfs-converter-cli/target/
    java -jar onebusaway-vdv452-to-gtfs-converter-cli.jar <path to .x10 files> <path to empty directory where GTFS will be dumped>

In Eclipse:
- set "project" to `onebusaway-vdv452-to-gtfs-converter-cli`
- set "main class" to `org.onebusaway.vdv452.Vdv452ToGtfsConverterMain`
- set "program arguments" to `"<path_to_.x10_files>" "<path_to_dump_gtfs>"` (taking care to quote both directory paths!)


**Note**: Converting large GTFS feeds is often processor and memory intensive.
You'll likely need to increase the max amount of memory allocated to Java with
an option like -Xmx1G (adjust the limit as needed). I also recommend adding the
-server argument if you are running the Oracle or OpenJDK, as it can really
increase performance.

### Arguments

* `input_vdv_path` - path to a zip file or directory containing VDV-452 .x10 files (note the lower-case x in .x10).  For zip files, all files must be in the root of the zip. 
* `output_gtfs_path` - path to a zip file or directory where the converted GTFS feed will be written.
