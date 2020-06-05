/**
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.vdv452.model;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.csv_entities.schema.annotations.CsvFields;
import org.onebusaway.vdv452.serialization.DegressMinutesSecondsFieldMappingFactory;
import org.onebusaway.vdv452.serialization.StopIdFieldMappingFactory;

@CsvFields(filename = "REC_ORT.x10")
public class Stop extends IdentityBean<StopId> {

  // easy switch to use ORT_REF_ORT_NAME instead of ORT_NAME
  public static boolean useRefName = false;

  private static final long serialVersionUID = 1L;

  @CsvField(name = "ZONE_WABE_NR", optional=true)
  private String zone;

  @CsvField(name = "ORT_NR", mapping = StopIdFieldMappingFactory.class)
  private StopId id;

  @CsvField(name = "ORT_NAME", optional=true)
  private String name;

  // This column could be prefered by human readers
  @CsvField(name = "ORT_REF_ORT_NAME", optional=true)
  private String refName;

  @CsvField(name = "ORT_POS_BREITE", mapping=DegressMinutesSecondsFieldMappingFactory.class)
  private double lat;

  @CsvField(name = "ORT_POS_LAENGE", mapping=DegressMinutesSecondsFieldMappingFactory.class)
  private double lng;

  @Override
  public StopId getId() {
    return id;
  }

  @Override
  public void setId(StopId id) {
    this.id = id;
  }

  public String getName() {
    // Could return refName instead.. configured by option?
    if (useRefName)
      return refName;
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setRefName(String refName) {
    this.refName = refName;
  }

  public double getLat() {
    return lat;
  }

  public void setLat(double lat) {
    this.lat = lat;
  }

  public double getLng() {
    return lng;
  }

  public void setLng(double lng) {
    this.lng = lng;
  }
  
  public String getZone() {
	    return zone;
  }

  public void setZone(String zone) {
	    this.zone = zone;
  }

}
