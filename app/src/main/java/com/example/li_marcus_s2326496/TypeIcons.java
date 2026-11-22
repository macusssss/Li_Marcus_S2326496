package com.example.li_marcus_s2326496;

public final class TypeIcons {
    private TypeIcons() { }

    public static int iconFor(TrafficEvent e) {
        switch (e.getTypeGroup()) {
            case ROAD_CLOSED:      return R.drawable.ic_type_closed;
            case LANES_RESTRICTED: return R.drawable.ic_type_lanes;
            case QUEUE:            return R.drawable.ic_type_queue;
            case ROADWORKS:        return R.drawable.ic_type_roadworks;
            default:               return R.drawable.ic_type_other;
        }
    }

    public static int labelFor(TrafficEvent e) {
        switch (e.getTypeGroup()) {
            case ROAD_CLOSED:      return R.string.type_closed;
            case LANES_RESTRICTED: return R.string.type_lanes;
            case QUEUE:            return R.string.type_queue;
            case ROADWORKS:        return R.string.type_roadworks;
            default:               return R.string.type_other;
        }
    }
}
