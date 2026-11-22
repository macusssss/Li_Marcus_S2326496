package com.example.li_marcus_s2326496;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TrafficEventParserTest {

    private static final String ONE_RECORD =
            "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<d2LogicalModel xmlns=\"http://datex2.eu/schema/2/2_0\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<payloadPublication xsi:type=\"SituationPublication\">"
            + "<situation id=\"S1\"><situationRecord xsi:type=\"RoadOrCarriagewayOrLaneManagement\" id=\"R1\">"
            + "<situationRecordVersionTime>2026-08-24T18:25:33+01:00</situationRecordVersionTime>"
            + "<probabilityOfOccurrence>certain</probabilityOfOccurrence>"
            + "<validity><validityTimeSpecification>"
            + "<overallStartTime>2026-08-10T11:37:42+01:00</overallStartTime>"
            + "</validityTimeSpecification></validity>"
            + "<impact><numberOfOperationalLanes>0</numberOfOperationalLanes></impact>"
            + "<generalPublicComment><comment><values><value lang=\"en\">J19 off slip closed.</value>"
            + "</values></comment></generalPublicComment>"
            + "<groupOfLocations xsi:type=\"Linear\"><tpegLinearLocation>"
            + "<tpegDirection>southBound</tpegDirection>"
            + "<to xsi:type=\"TpegJunction\"><pointCoordinates><latitude>55.065432</latitude>"
            + "<longitude>-3.273135</longitude></pointCoordinates>"
            + "<ilc><descriptor><values><value lang=\"en\">A74(M)</value></values></descriptor>"
            + "<tpegIlcPointDescriptorType>tpegIlcName1</tpegIlcPointDescriptorType></ilc>"
            + "<otherName><descriptor><values><value lang=\"en\">A74(M) J19 Eaglesfield</value></values>"
            + "</descriptor><tpegOtherPointDescriptorType>intersectionName</tpegOtherPointDescriptorType>"
            + "</otherName></to>"
            + "<from xsi:type=\"TpegJunction\"><pointCoordinates><latitude>55.103477</latitude>"
            + "<longitude>-3.349595</longitude></pointCoordinates>"
            + "<otherName><descriptor><values><value lang=\"en\">A74(M) J18 Ecclefechan</value></values>"
            + "</descriptor><tpegOtherPointDescriptorType>intersectionName</tpegOtherPointDescriptorType>"
            + "</otherName></from>"
            + "</tpegLinearLocation></groupOfLocations>"
            + "<complianceOption>mandatory</complianceOption>"
            + "<roadOrCarriagewayOrLaneManagementType>roadClosed</roadOrCarriagewayOrLaneManagementType>"
            + "</situationRecord></situation></payloadPublication></d2LogicalModel>";

    private static TrafficDataRepository repository() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new TrafficDataRepository(c);
    }

    private static TrafficDataRepository.DataSet setWithFile(String file) {
        for (TrafficDataRepository.DataSet set : repository().listDataSets()) {
            if (file.equals(set.unplannedFile) || file.equals(set.roadworksFile)) return set;
        }
        throw new AssertionError("missing data file " + file);
    }

    private static List<TrafficEvent> parseFile(String file, TrafficEvent.Category c) throws Exception {
        return TrafficEventParser.parse(repository().readXml(setWithFile(file), c), c);
    }

    @Test
    public void parsesEveryFieldOfARecord() throws Exception {
        List<TrafficEvent> events = TrafficEventParser.parse(ONE_RECORD, TrafficEvent.Category.UNPLANNED);
        assertEquals(1, events.size());
        TrafficEvent e = events.get(0);

        assertEquals("S1", e.getSituationId());
        assertEquals("R1", e.getRecordId());
        assertEquals("RoadOrCarriagewayOrLaneManagement", e.getRecordType());
        assertEquals("roadClosed", e.getManagementType());
        assertEquals("certain", e.getProbability());
        assertEquals("mandatory", e.getComplianceOption());
        assertEquals("2026-08-10T11:37:42+01:00", e.getStartTime());
        assertEquals(Integer.valueOf(0), e.getOperationalLanes());
        assertEquals("J19 off slip closed.", e.getDescription());
        assertEquals("southBound", e.getDirection());
        assertEquals("A74(M)", e.getRoad());
        assertEquals("A74(M) J18 Ecclefechan", e.getFromName());
        assertEquals("A74(M) J19 Eaglesfield", e.getToName());
        assertEquals(55.103477, e.getFromLatitude(), 1e-6);
        assertEquals(-3.349595, e.getFromLongitude(), 1e-6);
        assertEquals(55.065432, e.getToLatitude(), 1e-6);
        assertEquals(-3.273135, e.getToLongitude(), 1e-6);
    }

    @Test
    public void findsEveryBundledDataSetNewestLast() {
        List<TrafficDataRepository.DataSet> sets = repository().listDataSets();
        assertEquals(4, sets.size());
        for (TrafficDataRepository.DataSet set : sets) {
            assertNotNull(set.unplannedFile);
            assertNotNull(set.roadworksFile);
            assertNotNull(set.published);
        }
        for (int i = 1; i < sets.size(); i++) {
            assertFalse(sets.get(i).published.isBefore(sets.get(i - 1).published));
        }
    }

    @Test
    public void parsesBothUnplannedFiles() throws Exception {
        assertEquals(4, parseFile("unplannedContentExample.xml", TrafficEvent.Category.UNPLANNED).size());
        List<TrafficEvent> events = parseFile("unplannedContentExample1.xml", TrafficEvent.Category.UNPLANNED);
        assertEquals(7, events.size());
        int abnormal = 0;
        for (TrafficEvent e : events) {
            assertNotNull(e.getDescription());
            assertNotNull(e.getRoad());
            assertNotNull(e.getStartDateTime());
            assertTrue(e.hasCoordinates());
            if ("AbnormalTraffic".equals(e.getRecordType())) abnormal++;
        }
        assertEquals(5, abnormal);
    }

    @Test
    public void parsesBothRoadworksFiles() throws Exception {
        assertEquals(405, parseFile("roadworksContentExample.xml", TrafficEvent.Category.ROADWORKS).size());
        List<TrafficEvent> events = parseFile("roadworksContentExample1.xml", TrafficEvent.Category.ROADWORKS);
        assertEquals(452, events.size());
        for (TrafficEvent e : events) {
            assertEquals("MaintenanceWorks", e.getRecordType());
            assertNotNull(e.getDescription());
            assertNotNull(e.getStartDateTime());
            assertNotNull(e.getEndDateTime());
            assertNotNull(e.getUrgentRoadworks());
            assertTrue(e.hasCoordinates());
        }
    }

    @Test
    public void parsesObstructionsInNewerFiles() throws Exception {
        assertEquals(14, parseFile("unplannedContentExample2.xml", TrafficEvent.Category.UNPLANNED).size());
        List<TrafficEvent> events = parseFile("unplannedContentExample3.xml", TrafficEvent.Category.UNPLANNED);
        assertEquals(9, events.size());
        int brokenDown = 0;
        int onTheRoad = 0;
        for (TrafficEvent e : events) {
            if ("brokenDownVehicle".equals(e.getObstructionType())) brokenDown++;
            if ("obstructionOnTheRoad".equals(e.getObstructionType())) onTheRoad++;
        }
        assertEquals(2, brokenDown);
        assertEquals(1, onTheRoad);
        assertEquals(337, parseFile("roadworksContentExample2.xml", TrafficEvent.Category.ROADWORKS).size());
        assertEquals(376, parseFile("roadworksContentExample3.xml", TrafficEvent.Category.ROADWORKS).size());
    }

    @Test
    public void readsPublicationTime() throws Exception {
        TrafficDataRepository.DataSet set = setWithFile("unplannedContentExample1.xml");
        String xml = repository().readXml(set, TrafficEvent.Category.UNPLANNED);
        assertEquals("2026-10-26T13:46:39+00:00", TrafficEventParser.parsePublicationTime(xml));
    }

    @Test
    public void emptyFeedGivesNoEvents() throws Exception {
        assertTrue(TrafficEventParser.parse("", TrafficEvent.Category.UNPLANNED).isEmpty());
    }

    @Test(expected = XmlPullParserException.class)
    public void brokenXmlIsReported() throws Exception {
        TrafficEventParser.parse("<?xml version=\"1.0\"?><a><b></a>", TrafficEvent.Category.UNPLANNED);
    }
}
