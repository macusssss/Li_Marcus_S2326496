package com.example.li_marcus_s2326496;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class TrafficEventAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final Context context;
    private List<TrafficEvent> events = new ArrayList<>();

    public TrafficEventAdapter(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setEvents(List<TrafficEvent> events) {
        this.events = events;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return events.size(); }
    @Override public TrafficEvent getItem(int position) { return events.get(position); }
    @Override public long getItemId(int position) { return position; }

    private static class Holder {
        View severity;
        ImageView icon;
        TextView title;
        TextView type;
        TextView description;
        TextView dates;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_event, parent, false);
            h = new Holder();
            h.severity = convertView.findViewById(R.id.severityBar);
            h.icon = convertView.findViewById(R.id.itemIcon);
            h.title = convertView.findViewById(R.id.itemTitle);
            h.type = convertView.findViewById(R.id.itemType);
            h.description = convertView.findViewById(R.id.itemDescription);
            h.dates = convertView.findViewById(R.id.itemDates);
            convertView.setTag(h);
        } else {
            h = (Holder) convertView.getTag();
        }

        TrafficEvent e = getItem(position);
        h.severity.setBackgroundColor(SeverityColours.colourFor(context, e.getSeverity()));
        h.icon.setImageResource(TypeIcons.iconFor(e));
        h.icon.setContentDescription(context.getString(TypeIcons.labelFor(e)));
        h.title.setText(e.getTitle());
        String type = e.getEventTypeDisplay();
        if (e.getDelaysType() != null) type += " · " + TrafficEvent.humanise(e.getDelaysType());
        if (Boolean.TRUE.equals(e.getUrgentRoadworks())) type += " · " + context.getString(R.string.urgent);
        h.type.setText(type);
        h.description.setText(e.getDescription());
        String dates = context.getString(R.string.from_date, e.getStartDisplay());
        if (e.getEndTime() != null) dates += "\n" + context.getString(R.string.until_date, e.getEndDisplay());
        if (e.getDistanceKm() != null) dates += "\n" + context.getString(R.string.km_away, e.getDistanceKm());
        h.dates.setText(dates);
        return convertView;
    }
}
