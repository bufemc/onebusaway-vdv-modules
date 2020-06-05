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
package org.onebusaway.vdv452;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Logger;

import org.onebusaway.collections.MappingLibrary;
import org.onebusaway.collections.tuple.Pair;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Route;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.GtfsMutableRelationalDao;
import org.onebusaway.gtfs_transformer.updates.CalendarSimplicationLibrary;
import org.onebusaway.gtfs_transformer.updates.CalendarSimplicationLibrary.ServiceCalendarSummary;
import org.onebusaway.vdv452.model.*;

public class Vdv452ToGtfsFactory {

  // Use first coordinate as reference, to detect swapped coordinates and re-swap them
  private static double refLon = 0.0;
  private static double refLat = 0.0;
  private final double 	refMaxThreshold = 10.0;

  private final CalendarSimplicationLibrary _calendarLibrary = new CalendarSimplicationLibrary();

  private final Vdv452Dao _in;

  private final GtfsMutableRelationalDao _out;

  private final TimeZone _tz;

  /**
   * The set of service ids for calendar entries that have already been
   * processed.
   */
  private final Set<AgencyAndId> processedCalendars = new HashSet<AgencyAndId>();

  public Vdv452ToGtfsFactory(Vdv452Dao in, GtfsMutableRelationalDao out,
      TimeZone tz) {
    _in = in;
    _out = out;
    _tz = tz;
  }

  // changed: will save the trip only if it has stops
  public Trip getTripForJourney(Journey journey) {

    VersionedId journeyId = journey.getId();
    AgencyAndId id = new AgencyAndId("1", Long.toString(journeyId.getId()));
    Trip trip = _out.getTripForId(id);
    if (trip == null) {
      trip = new Trip();
      trip.setId(id);
      trip.setRoute(getRouteForLine(journey.getLine()));
      trip.setServiceId(createCalendarEntriesForDayType(journey.getDayType()));
        List<RouteSequence> routeSequence = _in.getRouteSequenceForLine(journey.getLine());
      // in "region" headsign is sometimes set only for first stop!
      Destination destination = routeSequence.get(0).getDestination();
      if (destination != null) {
          trip.setTripHeadsign(destination.getHeadsign());
      }
      // trip.setTripHeadsign(journey.getRouteSeq.getHeadsign write if != null);
      boolean tripHasStops = getStopTimesForJourney(journey, trip);
      boolean tripIsNormalRide = (journey.getTripType() == 1);
      if (!tripIsNormalRide) {
          System.err.println("SKIP_TRIP: trip=" + journey.getId() + " for line=" + journey.getLine() + " has tripType=" + journey.getTripType());
      }
      int serviceType = journey.getServiceType();
      if (serviceType == 2) {
          // System.err.println("SUSPICIOUS: trip=" + journey.getId() + " for line=" + journey.getLine() + " has serviceType=" + journey.getServiceType());
      }
      if (tripHasStops && tripIsNormalRide) {
    	  _out.saveEntity(trip);
      }
    }
    return trip;
  }

  public Route getRouteForLine(Line line) {
    LineId lineId = line.getId();
    Agency agency = getAgencyForLine(line);
    AgencyAndId id = new AgencyAndId(agency.getId(),
        Long.toString(lineId.getLineId()));
    Route route = _out.getRouteForId(id);
    if (route == null) {
      route = new Route();
      route.setId(id);
      route.setAgency(agency);
      if (line.getShortName() != null && !line.getShortName().isEmpty()) {
        route.setShortName(line.getShortName());
      }
      if (line.getLongName() != null && !line.getLongName().isEmpty()) {
        route.setLongName(line.getLongName());
      }
      route.setType(3);
      _out.saveEntity(route);
    }
    return route;
  }

  private Agency getAgencyForLine(Line line) {
    // Right now, I'm not actually clear on how a Line is linked to a
    // TransportCompany. So for now, if there is more than one, we bail.
    Collection<TransportCompany> companies = _in.getAllTransportCompanies();
    if (companies.size() != 1) {
      throw new IllegalStateException(
          "If you are reading this, it means you have a VDV 452 feed without "
              + "exactly one entry in ZUL_VERKEHRSBETRIEB.x10.  I haven't yet "
              + "implemented support for this scenario but would like to.  Reach "
              + "out to bdferris@google.com and let's see if we can get this fixed.");
    }
    TransportCompany company = companies.iterator().next();
    return getAgencyForTransportCompany(company);
  }

  public Agency getAgencyForTransportCompany(TransportCompany company) {
    String agencyId = Long.toString(company.getId().getId());
    Agency agency = _out.getAgencyForId(agencyId);
    if (agency == null) {
      agency = new Agency();
      agency.setId(agencyId);
      agency.setName(company.getName());
      agency.setTimezone(_tz.getID());
      agency.setUrl("https://github.com/OneBusAway/onebusaway-vdv-modules");
      agency.setLang("de");
      _out.saveEntity(agency);
    }
    return agency;
  }

  public AgencyAndId createCalendarEntriesForDayType(DayType dayType) {
    AgencyAndId serviceId = getServiceIdForDayType(dayType);
    if (!processedCalendars.add(serviceId)) {
      return serviceId;
    }
    Set<ServiceDate> serviceDates = new HashSet<ServiceDate>();
    for (Period period : _in.getPeriodsForDayType(dayType)) {
      // Convert the VDV ServiceDate to a GTFS ServiceDate
      serviceDates.add(new ServiceDate(period.getDate().getAsCalendar(_tz)));
    }
    ServiceCalendarSummary summary = _calendarLibrary.getSummaryForServiceDates(serviceDates);
    List<Object> newEntities = new ArrayList<Object>();
    _calendarLibrary.computeSimplifiedCalendar(serviceId, summary, newEntities);
    for (Object entity : newEntities) {
      _out.saveOrUpdateEntity(entity);
    }
    return serviceId;
  }

  private AgencyAndId getServiceIdForDayType(DayType dayType) {
    return new AgencyAndId("1", Long.toString(dayType.getId().getId()));
  }

  public Stop getStopForStopPoint(StopPoint stopPoint) {
	  
	StopId stopId = null;
	try {
		stopId = stopPoint.getId();
	} 
	catch (Exception e) {
		e.printStackTrace();
	}
	
    AgencyAndId id = new AgencyAndId("1", Long.toString(stopId.getId()));
    Stop gtfsStop = _out.getStopForId(id);
    
    if (gtfsStop == null) {
    	
      gtfsStop = new Stop();
      gtfsStop.setId(id);
      org.onebusaway.vdv452.model.Stop vdvStop = _in.getStopForId(stopId);
      if (vdvStop == null) {
        throw new IllegalStateException("unknown stop: " + stopId);
      }
      
      gtfsStop.setName(vdvStop.getName());
      
      // Retrieve and save also the "Wabe" / zone_id in stops.txt
      String zoneId = vdvStop.getZone();
      if (zoneId != null) {
    	  gtfsStop.setZoneId(zoneId);
      }
      
      double lon = vdvStop.getLng();
      double lat = vdvStop.getLat();
      
      // For GTFS we are interested only in ONR_TYP_NR==1 ("Haltestelle"/station), but not in 2 ("Depot") etc.
      boolean isGtfsStop = (vdvStop.getId().getType() == org.onebusaway.vdv452.model.EStopType.STOP);
      
      // First coordinate is set as reference coordinate, to detect swapped coordinates (lon <-> lat)
      if (refLon == 0 && refLat == 0) {
    	  refLon = lon;
    	  refLat = lat;
      }
      
      if (isGtfsStop) {
    	  // Coordinate is 0?
	      if (lon == 0 || lat == 0) {
	    	  System.err.println("SKIP_STOP: coord is 0: id="
	    			  + gtfsStop.getId() + " name=" + gtfsStop.getName());
	      }
	      // If longitude and latitude of coordinate are swapped: re-swap it!
	      else if (Math.abs(refLon - lon) > refMaxThreshold || Math.abs(refLat - lat) > refMaxThreshold) {
	    	  System.err.println("SWAP_COORD: coord is swapped: id="
	    			  + gtfsStop.getId() + " name=" + gtfsStop.getName());
	    	  double tmp = lon;
	    	  lon = lat;
	    	  lat = tmp;
	      }
      }
      else {
    	  System.err.println("SKIP_STOP: ONR_TYP_NR!=1: id=" + gtfsStop.getId() + " name=" + gtfsStop.getName());
    	  // Additionally, we mark this as a no-coordinate
    	  lon = 0;
    	  lat = 0;
      }

      gtfsStop.setLon(lon);
      gtfsStop.setLat(lat);
      
      // Do not add neither "unset" coordinates with 0.0/0.0, neither irrelevant POIs etc, just stations
      if (isGtfsStop && lon != 0 && lat != 0) {
    	  _out.saveEntity(gtfsStop);
      }
    }
    return gtfsStop;
  }

  // Returns true if travel time makes sense and stops were added, otherwise returns false
  private boolean getStopTimesForJourney(Journey journey, Trip trip) {
    List<RouteSequence> sequence = _in.getRouteSequenceForLine(journey.getLine());
    List<TravelTime> travelTimes = orderTravelTimesForRouteSequence(sequence,
        _in.getTravelTimesForTimingGroup(journey.getTimingGroup()));
    List<WaitTime> waitTimes = orderWaitTimesForRouteSequence(sequence,
        _in.getWaitTimesForTimingGroup(journey.getTimingGroup()));
    List<JourneyWaitTime> journeyWaitTimes = _in.getWaitTimesForJourney(journey);

    // Keep in mind that countStops <= seqLen, they might be different, as we remove e.g. depot stops!
    int seqLen = sequence.size();

    /* Pre-check the trip and its stops for (fixes for "region"!)
    - if travel time sum is 0 for all stops, warn and skip
    - if sequence of a trip contains stops with ONR_TYP_NR != 1 mark them, don't count those
    - if sequence of a trip contains only one stop in the end, don't add it
    Example 1: trip_id 1975 contains 2 stops only, but sum of travel time is 0, so trip and stops shall be skipped.
    1975,510099,13:38:00,13:38:00,0
    1975,510002,13:38:00,13:38:00,1
    Example 2: trip_id 2455 or here "4:2455" (line 3, AB) contains a depot stop with wrong ONR_TYP_NR (2)
    and another with correct one (1). sequence = [1=4:DEPOT:150, 2=4:STOP:501121]. As the stop for
    depot is not counted, in the end the result is only 1 stop, so trip and stops shall be skipped.
     */
    int travelTimeSum = 0;
    int countStops = 0;
    boolean hasOtherOnrTypes = false;
    for (int i = 0; i < seqLen; ++i) {
        RouteSequence entry = sequence.get(i);
        StopPoint stop = entry.getStop();
        if (stop == null) {
            continue;
        }
        // For GTFS we are interested only in ONR_TYP_NR==1 ("Haltestelle"), but not in 2 ("Depot") etc.
        StopId stopId = stop.getId();
        org.onebusaway.vdv452.model.Stop vdvStop = _in.getStopForId(stopId);
        boolean isGtfsStop = (vdvStop.getId().getType() == org.onebusaway.vdv452.model.EStopType.STOP);
        if (!isGtfsStop) {
            // We do not need to log warn here as skipped stops are printed already in getStopForStopPoint
            hasOtherOnrTypes = true;
            continue;
        }
        // Only count stops with ONR_TYP_NR == 1 ("Haltestelle", passenger stop)
        countStops += 1;
        // Sum up travel time, last stop has no travel time to a not existing next one
        if (i + 1 < seqLen) {
          travelTimeSum += travelTimes.get(i).getTravelTime();
        }
    }

    boolean isTravelTimeZero = (travelTimeSum == 0);
    boolean isOneStopOnly = (countStops == 1);

    // Don't add any stops for a trip when travel time is 0 (e.g. trip 1975 has 2 stops 510099 and 510002, both
    // depart at 13:38:00. Also don't add a single stop, as otp also throws them away. We return false here to
    // tell the caller that no stops were added in both cases, it should then also do not create an entry in trips.txt.
    // Furthermore, if there are still stops in the sequence with ONR_TYP_NR != 1 we just warn about this,
    // but keep the full sequence (this never happens at the moment, but could happen if we have e.g. 2 passenger
    // stops and 1 not counted, but still in sequence available depot stop).
    if (isTravelTimeZero) {
    	System.err.println("SKIP_TRIP: travel time is 0: "+journey.toString());
    	return false;
    }
    else if (isOneStopOnly) {
    	System.err.println("SKIP_TRIP: stop count is 1: "+journey.toString());
    	return false;
    }
    else {
        if (hasOtherOnrTypes) {
            System.err.println("WARN: trip still contains stops with ONR_TYP_NR != 1: "+journey.toString()+" Sequence: "+sequence);
        }
        int currentTime = journey.getDepartureTime();
	    for (int i = 0; i < seqLen; ++i) {
	      RouteSequence entry = sequence.get(i);
	      // In case of "region" stop is sometimes null, although a stop_sequence is set, don't know why (yet)
	      StopPoint stop = entry.getStop();
	      if (stop == null) {
	    	  // log warn here?
	    	  continue;
	      }
	      StopTime stopTime = new StopTime();
	      stopTime.setTrip(trip);
	      stopTime.setStop(getStopForStopPoint(entry.getStop()));
	      stopTime.setStopSequence(i);
	      stopTime.setArrivalTime(currentTime);
	      WaitTime waitTime = waitTimes.get(i);
	      if (waitTime != null) {
	        currentTime += waitTime.getWaitTime();
	      }

	      int jwtLen = journeyWaitTimes.size();
	      for (int j = 0; j < jwtLen; j++) {
	          JourneyWaitTime jwt = journeyWaitTimes.get(j);
	          if (jwt.getStop() == stop) {
                  System.out.println("ADD_JWT: adding waiting time for journey="+journey.toString()+" stop="+stop);
	              currentTime += jwt.getWaitTime();
	              break;
              }
	      }

	      stopTime.setDepartureTime(currentTime);
	      if (i + 1 < seqLen) {
	        currentTime += travelTimes.get(i).getTravelTime();
	      }

	      StopId stopId = stop.getId();
	      org.onebusaway.vdv452.model.Stop vdvStop = _in.getStopForId(stopId);

	      // for GTFS we are interested only in ONR_TYP_NR==1 ("Haltestelle"), but not in 2 ("Depot") etc.
	      boolean isGtfsStop = (vdvStop.getId().getType() == org.onebusaway.vdv452.model.EStopType.STOP);
	      boolean tripIsNormalRide = (journey.getTripType() == 1);

          // Do not add "unset" coordinates with 0.0/0.0 and do add only passenger stops
          if (isGtfsStop && tripIsNormalRide && vdvStop.getLng() != 0 && vdvStop.getLat() != 0) {
	    	  _out.saveEntity(stopTime);
	      }
	    }
	    return true; // success, stops were added
    }
  }

  private List<TravelTime> orderTravelTimesForRouteSequence(
      List<RouteSequence> sequence,
      Map<Pair<StopPoint>, TravelTime> travelTimesByStopPair) {
    List<TravelTime> ordered = new ArrayList<TravelTime>(sequence.size() - 1);
    for (int i = 0; i + 1 < sequence.size(); ++i) {
      RouteSequence from = sequence.get(i);
      RouteSequence to = sequence.get(i + 1);
      Pair<StopPoint> pair = Tuples.pair(from.getStop(), to.getStop());
      TravelTime travelTime = travelTimesByStopPair.get(pair);
      if (travelTime == null) {
        throw new IllegalStateException();
      }
      ordered.add(travelTime);
    }
    return ordered;
  }

  private List<WaitTime> orderWaitTimesForRouteSequence(
      List<RouteSequence> sequence, List<WaitTime> waitTimes) {
    Map<StopPoint, WaitTime> waitTimesByStopPoint = MappingLibrary.mapToValue(
        waitTimes, "stop");
    List<WaitTime> ordered = new ArrayList<WaitTime>(waitTimes.size());
    for (int i = 0; i < sequence.size(); ++i) {
      RouteSequence entry = sequence.get(i);
      WaitTime waitTime = waitTimesByStopPoint.get(entry.getStop());
      ordered.add(waitTime);
    }
    return ordered;
  }
}
