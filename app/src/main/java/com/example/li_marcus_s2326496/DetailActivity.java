package com.example.li_marcus_s2326496;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMapOptions;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    public static final String EXTRA_EVENT = "com.example.li_marcus_s2326496.EVENT";

    private LinearLayout rows;
    private LayoutInflater inflater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TrafficEvent e = readEvent();
        if (e == null) {
            finish();
            return;
        }

        inflater = LayoutInflater.from(this);
        rows = findViewById(R.id.detailRows);

        ((TextView) findViewById(R.id.detailTitle)).setText(e.getTitle());
        ImageView icon = findViewById(R.id.detailIcon);
        icon.setImageResource(TypeIcons.iconFor(e));
        icon.setContentDescription(getString(TypeIcons.labelFor(e)));
        TextView badge = findViewById(R.id.detailSeverity);
        badge.setText(getString(SeverityColours.labelFor(e.getSeverity())) + " · " + e.getEventTypeDisplay());
        badge.setBackgroundColor(SeverityColours.colourFor(this, e.getSeverity()));
        ((TextView) findViewById(R.id.detailDescription)).setText(e.getDescription());

        addRow(R.string.label_feed, getString(e.getCategory() == TrafficEvent.Category.UNPLANNED
                ? R.string.kind_unplanned_single : R.string.kind_roadworks_single));
        addRow(R.string.label_category, getString(TypeIcons.labelFor(e)));
        addRow(R.string.label_from, e.getFromName());
        addRow(R.string.label_to, e.getToName());
        addRow(R.string.label_start, e.getStartDisplay());
        if (e.getEndTime() != null) addRow(R.string.label_end, e.getEndDisplay());
        addRow(R.string.label_duration, e.getDurationDisplay());
        if (e.getDelaysType() != null) addRow(R.string.label_delays, TrafficEvent.humanise(e.getDelaysType()));
        if (e.getOperationalLanes() != null)
            addRow(R.string.label_operational_lanes, String.valueOf(e.getOperationalLanes()));
        if (e.getLanesRestricted() != null)
            addRow(R.string.label_lanes_restricted, String.valueOf(e.getLanesRestricted()));
        if (e.getUrgentRoadworks() != null)
            addRow(R.string.label_urgent, getString(e.getUrgentRoadworks() ? R.string.yes : R.string.no));
        if (e.getDistanceKm() != null)
            addRow(R.string.label_distance, getString(R.string.km_away, e.getDistanceKm()));
        if (e.getProbability() != null) addRow(R.string.label_probability, TrafficEvent.humanise(e.getProbability()));
        if (e.hasCoordinates()) {
            addRow(R.string.label_coords_from, formatCoords(e.getFromLatitude(), e.getFromLongitude()));
            addRow(R.string.label_coords_to, formatCoords(e.getToLatitude(), e.getToLongitude()));
        }
        if (e.getVersionTime() != null) {
            TrafficEvent tmp = new TrafficEvent(e.getCategory());
            tmp.setStartTime(e.getVersionTime());
            addRow(R.string.label_updated, tmp.getStartDisplay());
        }
        addRow(R.string.label_reference, e.getRecordId());

        setUpMap(e, savedInstanceState);
    }

    private void setUpMap(TrafficEvent e, Bundle savedInstanceState) {
        View container = findViewById(R.id.detailMap);
        if (!e.hasCoordinates()) {
            container.setVisibility(View.GONE);
            return;
        }
        CameraPosition camera = CameraPosition.fromLatLngZoom(MapHelper.position(e), zoomFor(e));
        SupportMapFragment fragment;
        if (savedInstanceState == null) {
            fragment = SupportMapFragment.newInstance(
                    new GoogleMapOptions().liteMode(true).camera(camera));
            getSupportFragmentManager().beginTransaction().replace(R.id.detailMap, fragment).commitNow();
        } else {
            fragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.detailMap);
        }
        if (fragment == null) return;

        fragment.getMapAsync(map -> {
            LatLng from = MapHelper.from(e);
            LatLng to = MapHelper.to(e);
            BitmapDescriptor icon = MapHelper.markerFor(e.getSeverity());
            if (from != null) {
                map.addMarker(new MarkerOptions().position(from).icon(icon)
                        .title(getString(R.string.label_from) + ": " + e.getFromName()));
            }
            if (to != null) {
                map.addMarker(new MarkerOptions().position(to).icon(icon)
                        .title(getString(R.string.label_to) + ": " + e.getToName()));
            }
            if (from != null && to != null) {
                map.addPolyline(new PolylineOptions().add(from, to).width(10f)
                        .color(SeverityColours.colourFor(this, e.getSeverity())));
            }
            map.moveCamera(CameraUpdateFactory.newCameraPosition(camera));
        });
    }

    private static float zoomFor(TrafficEvent e) {
        LatLng a = MapHelper.from(e);
        LatLng b = MapHelper.to(e);
        if (a == null || b == null) return 14f;
        double span = Math.max(Math.abs(a.latitude - b.latitude),
                Math.abs(a.longitude - b.longitude) * 0.56);
        if (span < 1e-6) return 14f;
        double zoom = Math.floor(Math.log(1.0 / span) / Math.log(2)) + 7;
        return (float) Math.max(7, Math.min(15, zoom));
    }

    @SuppressWarnings("deprecation")
    private TrafficEvent readEvent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(EXTRA_EVENT, TrafficEvent.class);
        }
        return (TrafficEvent) getIntent().getSerializableExtra(EXTRA_EVENT);
    }

    private static String formatCoords(Double lat, Double lon) {
        if (lat == null || lon == null) return null;
        return String.format(Locale.UK, "%.5f, %.5f", lat, lon);
    }

    private void addRow(int labelRes, String value) {
        if (value == null || value.isEmpty()) return;
        View row = inflater.inflate(R.layout.item_detail_row, rows, false);
        ((TextView) row.findViewById(R.id.rowLabel)).setText(labelRes);
        ((TextView) row.findViewById(R.id.rowValue)).setText(value);
        rows.addView(row);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
