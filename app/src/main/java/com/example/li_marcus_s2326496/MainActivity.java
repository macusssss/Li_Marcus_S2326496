/*  Starter project for Mobile Platform Development - 1st diet 26/27
    You should use this project as the starting point for your assignment.
    This project simply reads the data from the required source and displays the
    raw data in a TextField
*/

//
// Name                 Marcus Li
// Student ID           S2326496
// Programme of Study   BSc Software Development
//

// UPDATE THE PACKAGE NAME to include your Student Identifier
package com.example.li_marcus_s2326496;

import android.Manifest;
import android.app.DatePickerDialog;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final DateTimeFormatter DATE_BUTTON = DateTimeFormatter.ofPattern("EEE d MMM yyyy");

    private TrafficViewModel viewModel;
    private TrafficEventAdapter adapter;

    private Button unplannedButton;
    private Button roadworksButton;
    private Button dateButton;
    private Button clearDateButton;
    private TextView statusDisplay;
    private TextView emptyView;
    private ProgressBar progress;
    private ListView list;
    private View mapView;

    private GoogleMap map;
    private List<TrafficEvent> currentEvents = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Set up the raw links to the graphical components
        unplannedButton = findViewById(R.id.unplannedButton);
        roadworksButton = findViewById(R.id.roadworksButton);
        dateButton = findViewById(R.id.dateButton);
        clearDateButton = findViewById(R.id.clearDateButton);
        EditText searchBox = findViewById(R.id.searchBox);
        statusDisplay = findViewById(R.id.statusDisplay);
        progress = findViewById(R.id.progress);
        emptyView = findViewById(R.id.emptyView);
        list = findViewById(R.id.eventList);
        mapView = findViewById(R.id.map);

        adapter = new TrafficEventAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> openDetail(adapter.getItem(position)));

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        viewModel = new ViewModelProvider(this).get(TrafficViewModel.class);
        viewModel.getState().observe(this, this::render);

        unplannedButton.setOnClickListener(v -> selectCategory(TrafficEvent.Category.UNPLANNED));
        roadworksButton.setOnClickListener(v -> selectCategory(TrafficEvent.Category.ROADWORKS));
        dateButton.setOnClickListener(v -> pickDate());
        clearDateButton.setOnClickListener(v -> {
            viewModel.setDate(null);
            updateDateButton();
        });

        setUpDistanceSpinner();

        searchBox.setText(viewModel.getQuery());
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { viewModel.setQuery(s.toString()); }
        });

        updateDateButton();
        applyViewMode();
        selectCategory(viewModel.getCategory());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_toggle_map)
                .setTitle(viewModel.isShowMap() ? R.string.show_list : R.string.show_map);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_map) {
            viewModel.setShowMap(!viewModel.isShowMap());
            applyViewMode();
            invalidateOptionsMenu();
            return true;
        } else if (item.getItemId() == R.id.action_refresh) {
            viewModel.selectCategory(viewModel.getCategory(), true);
            return true;
        } else if (item.getItemId() == R.id.action_data_set) {
            chooseDataSet();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyViewMode() {
        boolean showMap = viewModel.isShowMap();
        mapView.setVisibility(showMap ? View.VISIBLE : View.GONE);
        if (showMap) {
            list.setEmptyView(null);
            list.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            updateMarkers();
        } else {
            list.setVisibility(View.VISIBLE);
            list.setEmptyView(emptyView);
        }
    }

    private static final int[] RADII_KM = { 0, 5, 10, 25, 50, 100 };

    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.location_denied, Toast.LENGTH_LONG).show();
                }
                fetchLocation();
            });

    private void setUpDistanceSpinner() {
        Spinner spinner = findViewById(R.id.distanceSpinner);
        String[] labels = new String[RADII_KM.length];
        for (int i = 0; i < RADII_KM.length; i++) {
            labels[i] = RADII_KM[i] == 0 ? getString(R.string.distance_any)
                    : getString(R.string.distance_within, RADII_KM[i]);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
        for (int i = 0; i < RADII_KM.length; i++) {
            if (RADII_KM[i] == viewModel.getRadiusKm()) spinner.setSelection(i, false);
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int km = RADII_KM[position];
                if (km == viewModel.getRadiusKm()) return;
                viewModel.setRadiusKm(km);
                if (km > 0 && !viewModel.hasOrigin()) {
                    if (LocationHelper.hasPermission(MainActivity.this)) fetchLocation();
                    else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void fetchLocation() {
        LocationHelper.getLocation(this, (lat, lon, fallback) -> {
            viewModel.setOrigin(lat, lon, fallback);
            if (fallback) Toast.makeText(this, R.string.using_fallback, Toast.LENGTH_LONG).show();
        });
    }

    private void chooseDataSet() {
        List<String> labels = viewModel.getDataSetLabels();
        if (labels.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_data_set)
                .setSingleChoiceItems(labels.toArray(new String[0]), viewModel.getDataSetIndex(), (d, which) -> {
                    viewModel.selectDataSet(which);
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void selectCategory(TrafficEvent.Category c) {
        viewModel.selectCategory(c, false);
        boolean unplanned = c == TrafficEvent.Category.UNPLANNED;
        highlight(unplannedButton, unplanned);
        highlight(roadworksButton, !unplanned);
    }

    private void highlight(Button b, boolean selected) {
        b.setAlpha(selected ? 1f : 0.55f);
        b.setSelected(selected);
    }

    private void pickDate() {
        LocalDate d = viewModel.getDate() != null ? viewModel.getDate() : LocalDate.now();
        new DatePickerDialog(this, (picker, y, m, day) -> {
            viewModel.setDate(LocalDate.of(y, m + 1, day));
            updateDateButton();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void updateDateButton() {
        LocalDate d = viewModel.getDate();
        dateButton.setText(d == null ? getString(R.string.any_date)
                : getString(R.string.on_date, d.format(DATE_BUTTON)));
        clearDateButton.setVisibility(d == null ? View.GONE : View.VISIBLE);
    }

    private void render(TrafficViewModel.UiState s) {
        progress.setVisibility(s.loading ? View.VISIBLE : View.GONE);
        statusDisplay.setText(s.status);
        adapter.setEvents(s.events);
        currentEvents = s.events;
        unplannedButton.setEnabled(!s.loading);
        roadworksButton.setEnabled(!s.loading);
        if (viewModel.isShowMap()) updateMarkers();
    }

    private void openDetail(TrafficEvent e) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_EVENT, e);
        startActivity(i);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        map.setOnInfoWindowClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof TrafficEvent) openDetail((TrafficEvent) tag);
        });
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(MapHelper.SCOTLAND, MapHelper.SCOTLAND_ZOOM));
        if (viewModel.isShowMap()) updateMarkers();
    }

    private void updateMarkers() {
        if (map == null) return;
        map.clear();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int count = 0;
        for (TrafficEvent e : currentEvents) {
            LatLng pos = MapHelper.position(e);
            if (pos == null) continue;
            Marker m = map.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(e.getTitle())
                    .snippet(e.getEventTypeDisplay() + " · " + getString(R.string.tap_for_details))
                    .icon(MapHelper.markerFor(e.getSeverity())));
            if (m != null) m.setTag(e);
            bounds.include(pos);
            count++;
        }
        if (viewModel.getRadiusKm() > 0 && viewModel.hasOrigin()) {
            LatLng me = new LatLng(viewModel.getOriginLat(), viewModel.getOriginLon());
            map.addMarker(new MarkerOptions().position(me)
                    .title(getString(viewModel.isOriginFallback() ? R.string.fallback_marker : R.string.you_marker))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            map.addCircle(new CircleOptions().center(me).radius(viewModel.getRadiusKm() * 1000.0)
                    .strokeColor(0xAA1E88E5).strokeWidth(4f).fillColor(0x221E88E5));
            double dLat = viewModel.getRadiusKm() / 111.0;
            double dLon = dLat / Math.cos(Math.toRadians(me.latitude));
            bounds.include(new LatLng(me.latitude + dLat, me.longitude));
            bounds.include(new LatLng(me.latitude - dLat, me.longitude));
            bounds.include(new LatLng(me.latitude, me.longitude + dLon));
            bounds.include(new LatLng(me.latitude, me.longitude - dLon));
            count += 2;
        }
        if (count == 0) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(MapHelper.SCOTLAND, MapHelper.SCOTLAND_ZOOM));
        } else if (count == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.build().getCenter(), 12f));
        } else {
            final LatLngBounds b = bounds.build();
            mapView.post(() -> {
                try {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(b, 80));
                } catch (IllegalStateException notLaidOut) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(b.getCenter(), 7f));
                }
            });
        }
    }
}
