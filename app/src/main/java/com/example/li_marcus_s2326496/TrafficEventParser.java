package com.example.li_marcus_s2326496;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class TrafficEventParser {
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private TrafficEventParser() { }

    public static String cleanXml(String raw) {
        if (raw == null) return "";
        int i = raw.indexOf("<?");
        return i > 0 ? raw.substring(i) : raw;
    }

    public static String parsePublicationTime(String xml) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(cleanXml(xml)));
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && "publicationTime".equals(xpp.getName())) {
                return xpp.nextText().trim();
            }
            eventType = xpp.next();
        }
        return null;
    }

    public static List<TrafficEvent> parse(String xml, TrafficEvent.Category category)
            throws XmlPullParserException, IOException {
        List<TrafficEvent> events = new ArrayList<>();

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(cleanXml(xml)));

        TrafficEvent current = null;
        String situationId = null;
        String point = null;
        boolean inComment = false;
        String pendingDescriptor = null;
        StringBuilder text = new StringBuilder();

        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            String name = xpp.getName();

            if (eventType == XmlPullParser.START_TAG) {
                text.setLength(0);
                switch (name) {
                    case "situation":
                        situationId = xpp.getAttributeValue(null, "id");
                        break;
                    case "situationRecord":
                        current = new TrafficEvent(category);
                        current.setSituationId(situationId);
                        current.setRecordId(xpp.getAttributeValue(null, "id"));
                        current.setRecordType(xpp.getAttributeValue(XSI_NS, "type"));
                        break;
                    case "from":
                    case "to":
                        point = name;
                        break;
                    case "generalPublicComment":
                        inComment = true;
                        break;
                    default:
                        break;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                text.append(xpp.getText());
            } else if (eventType == XmlPullParser.END_TAG && current != null) {
                String value = text.toString().trim();
                text.setLength(0);

                switch (name) {
                    case "situationRecord":
                        events.add(current);
                        current = null;
                        break;
                    case "situationRecordVersionTime":
                        current.setVersionTime(value);
                        break;
                    case "probabilityOfOccurrence":
                        current.setProbability(value);
                        break;
                    case "overallStartTime":
                        current.setStartTime(value);
                        break;
                    case "overallEndTime":
                        current.setEndTime(value);
                        break;
                    case "delaysType":
                        current.setDelaysType(value);
                        break;
                    case "numberOfOperationalLanes":
                        current.setOperationalLanes(parseInt(value));
                        break;
                    case "numberOfLanesRestricted":
                        current.setLanesRestricted(parseInt(value));
                        break;
                    case "roadOrCarriagewayOrLaneManagementType":
                        current.setManagementType(value);
                        break;
                    case "abnormalTrafficType":
                        current.setAbnormalTrafficType(value);
                        break;
                    case "roadMaintenanceType":
                        current.setMaintenanceType(value);
                        break;
                    case "obstructionType":
                    case "vehicleObstructionType":
                        current.setObstructionType(value);
                        break;
                    case "urgentRoadworks":
                        current.setUrgentRoadworks(Boolean.parseBoolean(value));
                        break;
                    case "complianceOption":
                        current.setComplianceOption(value);
                        break;
                    case "tpegDirection":
                        current.setDirection(value);
                        break;
                    case "generalPublicComment":
                        inComment = false;
                        break;
                    case "value":
                        if (inComment) {
                            current.setDescription(value);
                        } else if (point != null) {
                            pendingDescriptor = value;
                        }
                        break;
                    case "latitude":
                        if ("from".equals(point)) current.setFromLatitude(parseDouble(value));
                        else if ("to".equals(point)) current.setToLatitude(parseDouble(value));
                        break;
                    case "longitude":
                        if ("from".equals(point)) current.setFromLongitude(parseDouble(value));
                        else if ("to".equals(point)) current.setToLongitude(parseDouble(value));
                        break;
                    case "tpegOtherPointDescriptorType":
                    case "tpegJunctionPointDescriptorType":
                    case "tpegIlcPointDescriptorType":
                        applyDescriptor(current, point, value, pendingDescriptor);
                        pendingDescriptor = null;
                        break;
                    case "from":
                    case "to":
                        point = null;
                        break;
                    default:
                        break;
                }
            }
            eventType = xpp.next();
        }
        return events;
    }

    private static void applyDescriptor(TrafficEvent e, String point, String type, String value) {
        if (point == null || value == null || value.isEmpty()) return;
        switch (type) {
            case "linkName":
            case "tpegIlcName1":
                if (e.getRoad() == null) e.setRoad(value);
                break;
            case "nonLinkedPointName":
            case "intersectionName":
                setPointName(e, point, value, true);
                break;
            case "junctionName":
                setPointName(e, point, value, false);
                break;
            default:
                setPointName(e, point, value, false);
                break;
        }
    }

    private static void setPointName(TrafficEvent e, String point, String value, boolean preferred) {
        if ("from".equals(point)) {
            if (preferred || e.getFromName() == null) e.setFromName(value);
        } else {
            if (preferred || e.getToName() == null) e.setToName(value);
        }
    }

    private static Integer parseInt(String s) {
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(String s) {
        try { return Double.valueOf(s); } catch (NumberFormatException e) { return null; }
    }
}
