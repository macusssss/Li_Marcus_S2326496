package com.example.li_marcus_s2326496;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

public class TrafficEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Category { UNPLANNED, ROADWORKS }

    public enum Severity { HIGH, MEDIUM, LOW }

    private static final ZoneId UK = ZoneId.of("Europe/London");

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm");

    private final Category category;

    private String situationId;
    private String recordId;
    private String recordType;
    private String versionTime;
    private String probability;

    private String startTime;
    private String endTime;

    private String delaysType;
    private Integer operationalLanes;
    private Integer lanesRestricted;

    private String description;

    private String managementType;
    private String abnormalTrafficType;
    private String maintenanceType;
    private String obstructionType;
    private Boolean urgentRoadworks;
    private String complianceOption;

    private String direction;
    private String road;
    private String fromName;
    private String toName;
    private Double fromLatitude;
    private Double fromLongitude;
    private Double toLatitude;
    private Double toLongitude;

    public TrafficEvent(Category category) {
        this.category = category;
    }

    public String getEventType() {
        if (managementType != null) return managementType;
        if (abnormalTrafficType != null) return abnormalTrafficType;
        if (maintenanceType != null) return maintenanceType;
        if (obstructionType != null) return obstructionType;
        return recordType;
    }

    public OffsetDateTime getStartDateTime() { return parseDate(startTime); }
    public OffsetDateTime getEndDateTime() { return parseDate(endTime); }

    public String getStartDisplay() { return display(startTime); }
    public String getEndDisplay() { return display(endTime); }

    public boolean isActiveAt(OffsetDateTime when) {
        OffsetDateTime start = getStartDateTime();
        OffsetDateTime end = getEndDateTime();
        if (start != null && when.isBefore(start)) return false;
        return end == null || !when.isAfter(end);
    }

    public boolean isActiveOn(LocalDate day) {
        OffsetDateTime dayStart = day.atStartOfDay(UK).toOffsetDateTime();
        OffsetDateTime dayEnd = day.plusDays(1).atStartOfDay(UK).toOffsetDateTime();
        OffsetDateTime start = getStartDateTime();
        OffsetDateTime end = getEndDateTime();
        if (start != null && !start.isBefore(dayEnd)) return false;
        return end == null || end.isAfter(dayStart);
    }

    public boolean matches(String query) {
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim();
        String regex = "(?<![A-Za-z0-9])" + Pattern.quote(q)
                + (Character.isDigit(q.charAt(q.length() - 1)) ? "(?![0-9])" : "");
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return find(p, road) || find(p, fromName) || find(p, toName) || find(p, description);
    }

    private static boolean find(Pattern p, String field) {
        return field != null && p.matcher(field).find();
    }

    public Severity getSeverity() {
        if ("roadClosed".equals(managementType) || "longDelays".equals(delaysType)
                || Boolean.TRUE.equals(urgentRoadworks)
                || (operationalLanes != null && operationalLanes == 0)) {
            return Severity.HIGH;
        }
        if (delaysType != null || abnormalTrafficType != null || lanesRestricted != null
                || obstructionType != null) {
            return Severity.MEDIUM;
        }
        return Severity.LOW;
    }

    public String getTitle() {
        String t = road != null ? road : "Unknown road";
        return direction != null ? t + " " + humanise(direction).toLowerCase(Locale.UK) : t;
    }

    public String getEventTypeDisplay() {
        String t = getEventType();
        return t == null ? "Incident" : humanise(t);
    }

    public String getDurationDisplay() {
        OffsetDateTime start = getStartDateTime();
        OffsetDateTime end = getEndDateTime();
        if (start == null || end == null || end.isBefore(start)) return null;
        Duration d = Duration.between(start, end);
        long days = d.toDays();
        if (days >= 1) return days + (days == 1 ? " day" : " days");
        long hours = Math.max(1, d.toHours());
        return hours + (hours == 1 ? " hour" : " hours");
    }

    public static String humanise(String camel) {
        if (camel == null || camel.isEmpty()) return camel;
        if (camel.endsWith("Bound")) {
            String dir = camel.substring(0, camel.length() - 5);
            return Character.toUpperCase(dir.charAt(0)) + dir.substring(1) + "bound";
        }
        String spaced = camel.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.UK);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private Double distanceKm;

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double d) { distanceKm = d; }

    public double[] getMidpoint() {
        boolean hasFrom = fromLatitude != null && fromLongitude != null;
        boolean hasTo = toLatitude != null && toLongitude != null;
        if (hasFrom && hasTo) {
            return new double[] { (fromLatitude + toLatitude) / 2, (fromLongitude + toLongitude) / 2 };
        }
        if (hasFrom) return new double[] { fromLatitude, fromLongitude };
        if (hasTo) return new double[] { toLatitude, toLongitude };
        return null;
    }

    public Double distanceKmFrom(double lat, double lon) {
        double[] p = getMidpoint();
        if (p == null) return null;
        double dLat = Math.toRadians(p[0] - lat);
        double dLon = Math.toRadians(p[1] - lon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(p[0]))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public enum TypeGroup { ROAD_CLOSED, LANES_RESTRICTED, QUEUE, ROADWORKS, OTHER }

    public TypeGroup getTypeGroup() {
        if ("roadClosed".equals(managementType)
                || (operationalLanes != null && operationalLanes == 0)) {
            return TypeGroup.ROAD_CLOSED;
        }
        if (maintenanceType != null || "MaintenanceWorks".equals(recordType)) return TypeGroup.ROADWORKS;
        if (abnormalTrafficType != null || "AbnormalTraffic".equals(recordType)) return TypeGroup.QUEUE;
        if (managementType != null || lanesRestricted != null) return TypeGroup.LANES_RESTRICTED;
        return TypeGroup.OTHER;
    }

    public boolean hasCoordinates() {
        return (fromLatitude != null && fromLongitude != null)
                || (toLatitude != null && toLongitude != null);
    }

    private static OffsetDateTime parseDate(String s) {
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String display(String s) {
        OffsetDateTime d = parseDate(s);
        return d == null ? (s == null ? "n/a" : s) : d.format(DISPLAY_FORMAT);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(road != null ? road : "Unknown road");
        if (direction != null) sb.append(" (").append(direction).append(")");
        sb.append("\nType: ").append(getEventType());
        if (Boolean.TRUE.equals(urgentRoadworks)) sb.append(" [URGENT]");
        sb.append("\n").append(description != null ? description : "(no description)");
        if (fromName != null || toName != null) {
            sb.append("\nFrom: ").append(fromName != null ? fromName : "?")
              .append("\nTo: ").append(toName != null ? toName : "?");
        }
        sb.append("\nStart: ").append(getStartDisplay());
        if (endTime != null) sb.append("\nEnd: ").append(getEndDisplay());
        if (delaysType != null) sb.append("\nDelays: ").append(delaysType);
        if (operationalLanes != null) sb.append("\nOperational lanes: ").append(operationalLanes);
        if (lanesRestricted != null) sb.append("\nLanes restricted: ").append(lanesRestricted);
        if (hasCoordinates()) {
            sb.append("\nCoords: ")
              .append(fromLatitude).append(", ").append(fromLongitude)
              .append(" -> ")
              .append(toLatitude).append(", ").append(toLongitude);
        }
        return sb.toString();
    }

    public Category getCategory() { return category; }
    public String getSituationId() { return situationId; }
    public void setSituationId(String v) { situationId = v; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String v) { recordId = v; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String v) { recordType = v; }
    public String getVersionTime() { return versionTime; }
    public void setVersionTime(String v) { versionTime = v; }
    public String getProbability() { return probability; }
    public void setProbability(String v) { probability = v; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String v) { startTime = v; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String v) { endTime = v; }
    public String getDelaysType() { return delaysType; }
    public void setDelaysType(String v) { delaysType = v; }
    public Integer getOperationalLanes() { return operationalLanes; }
    public void setOperationalLanes(Integer v) { operationalLanes = v; }
    public Integer getLanesRestricted() { return lanesRestricted; }
    public void setLanesRestricted(Integer v) { lanesRestricted = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public String getManagementType() { return managementType; }
    public void setManagementType(String v) { managementType = v; }
    public String getAbnormalTrafficType() { return abnormalTrafficType; }
    public void setAbnormalTrafficType(String v) { abnormalTrafficType = v; }
    public String getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(String v) { maintenanceType = v; }
    public String getObstructionType() { return obstructionType; }
    public void setObstructionType(String v) { obstructionType = v; }
    public Boolean getUrgentRoadworks() { return urgentRoadworks; }
    public void setUrgentRoadworks(Boolean v) { urgentRoadworks = v; }
    public String getComplianceOption() { return complianceOption; }
    public void setComplianceOption(String v) { complianceOption = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { direction = v; }
    public String getRoad() { return road; }
    public void setRoad(String v) { road = v; }
    public String getFromName() { return fromName; }
    public void setFromName(String v) { fromName = v; }
    public String getToName() { return toName; }
    public void setToName(String v) { toName = v; }
    public Double getFromLatitude() { return fromLatitude; }
    public void setFromLatitude(Double v) { fromLatitude = v; }
    public Double getFromLongitude() { return fromLongitude; }
    public void setFromLongitude(Double v) { fromLongitude = v; }
    public Double getToLatitude() { return toLatitude; }
    public void setToLatitude(Double v) { toLatitude = v; }
    public Double getToLongitude() { return toLongitude; }
    public void setToLongitude(Double v) { toLongitude = v; }
}
