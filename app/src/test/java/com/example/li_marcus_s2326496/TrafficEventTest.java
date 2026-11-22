package com.example.li_marcus_s2326496;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public class TrafficEventTest {

    private static TrafficEvent roadworks(String start, String end) {
        TrafficEvent e = new TrafficEvent(TrafficEvent.Category.ROADWORKS);
        e.setRecordType("MaintenanceWorks");
        e.setMaintenanceType("roadworks");
        e.setStartTime(start);
        e.setEndTime(end);
        return e;
    }

    @Test
    public void humaniseTurnsCamelCaseIntoWords() {
        assertEquals("Road closed", TrafficEvent.humanise("roadClosed"));
        assertEquals("Long delays", TrafficEvent.humanise("longDelays"));
        assertEquals("Westbound", TrafficEvent.humanise("westBound"));
        assertEquals("Queuing traffic", TrafficEvent.humanise("queuingTraffic"));
    }

    @Test
    public void searchMatchesWholeRoadNumbersOnly() {
        TrafficEvent m8 = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        m8.setRoad("M8");
        TrafficEvent m80 = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        m80.setRoad("M80");

        assertTrue(m8.matches("M8"));
        assertTrue(m8.matches("m8"));
        assertFalse(m80.matches("M8"));
        assertTrue(m80.matches("M80"));
    }

    @Test
    public void searchMatchesPlaceNamesAndDescription() {
        TrafficEvent e = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        e.setRoad("A82");
        e.setFromName("Glasgow Road");
        e.setDescription("Lane closure near Dumbarton");

        assertTrue(e.matches("Glas"));
        assertTrue(e.matches("dumbarton"));
        assertTrue(e.matches(""));
        assertFalse(e.matches("Inverness"));
    }

    @Test
    public void activeOnDateUsesStartAndEnd() {
        TrafficEvent e = roadworks("2026-10-26T07:00:00+00:00", "2026-10-28T16:00:00+00:00");

        assertFalse(e.isActiveOn(LocalDate.of(2026, 10, 25)));
        assertTrue(e.isActiveOn(LocalDate.of(2026, 10, 26)));
        assertTrue(e.isActiveOn(LocalDate.of(2026, 10, 28)));
        assertFalse(e.isActiveOn(LocalDate.of(2026, 10, 29)));
    }

    @Test
    public void openEndedEventStaysActive() {
        TrafficEvent e = roadworks("2026-08-10T11:37:42+01:00", null);

        assertTrue(e.isActiveOn(LocalDate.of(2027, 1, 1)));
        assertNull(e.getDurationDisplay());
    }

    @Test
    public void durationIsShownInDaysOrHours() {
        assertEquals("2 days", roadworks("2026-10-26T07:00:00+00:00", "2026-10-28T16:00:00+00:00").getDurationDisplay());
        assertEquals("6 hours", roadworks("2026-10-26T10:00:00+00:00", "2026-10-26T16:00:00+00:00").getDurationDisplay());
    }

    @Test
    public void distanceBetweenGlasgowAndEdinburgh() {
        TrafficEvent e = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        e.setFromLatitude(55.9533);
        e.setFromLongitude(-3.1883);
        e.setToLatitude(55.9533);
        e.setToLongitude(-3.1883);

        Double km = e.distanceKmFrom(55.8611, -4.2502);
        assertNotNull(km);
        assertEquals(67.0, km, 3.0);
    }

    @Test
    public void distanceIsNullWithoutCoordinates() {
        assertNull(new TrafficEvent(TrafficEvent.Category.UNPLANNED).distanceKmFrom(55.86, -4.25));
    }

    @Test
    public void typeGroupAndSeverityFollowTheData() {
        TrafficEvent closed = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        closed.setRecordType("RoadOrCarriagewayOrLaneManagement");
        closed.setManagementType("roadClosed");
        closed.setOperationalLanes(0);
        assertEquals(TrafficEvent.TypeGroup.ROAD_CLOSED, closed.getTypeGroup());
        assertEquals(TrafficEvent.Severity.HIGH, closed.getSeverity());

        TrafficEvent queue = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        queue.setRecordType("AbnormalTraffic");
        queue.setAbnormalTrafficType("queuingTraffic");
        queue.setLanesRestricted(2);
        assertEquals(TrafficEvent.TypeGroup.QUEUE, queue.getTypeGroup());
        assertEquals(TrafficEvent.Severity.MEDIUM, queue.getSeverity());

        TrafficEvent works = roadworks("2026-10-26T07:00:00+00:00", "2026-10-28T16:00:00+00:00");
        assertEquals(TrafficEvent.TypeGroup.ROADWORKS, works.getTypeGroup());
        assertEquals(TrafficEvent.Severity.LOW, works.getSeverity());
        works.setDelaysType("longDelays");
        assertEquals(TrafficEvent.Severity.HIGH, works.getSeverity());
    }

    @Test
    public void titleCombinesRoadAndDirection() {
        TrafficEvent e = new TrafficEvent(TrafficEvent.Category.UNPLANNED);
        e.setRoad("A74(M)");
        e.setDirection("southBound");
        assertEquals("A74(M) southbound", e.getTitle());
    }

    @Test
    public void cleanXmlDropsLeadingGarbage() {
        assertEquals("<?xml version=\"1.0\"?><a/>", TrafficEventParser.cleanXml("﻿junk<?xml version=\"1.0\"?><a/>"));
        assertEquals("", TrafficEventParser.cleanXml(null));
    }
}
