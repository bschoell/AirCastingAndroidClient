package pl.llp.aircasting.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import com.google.inject.Inject;
import pl.llp.aircasting.Intents;
import pl.llp.aircasting.R;
import pl.llp.aircasting.activity.adapter.StreamAdapter;
import pl.llp.aircasting.activity.adapter.StreamAdapterFactory;
import pl.llp.aircasting.activity.extsens.ExternalSensorActivity;
import pl.llp.aircasting.model.*;
import roboguice.inject.InjectView;

import static pl.llp.aircasting.Intents.startSensors;
import static pl.llp.aircasting.Intents.stopSensors;

public class DashboardActivity extends ButtonsActivity {
    @Inject Context context;
    @Inject StreamAdapterFactory adapterFactory;
    @Inject SessionManager sessionManager;

    @InjectView(R.id.dashboard_microphone) Button microphoneButton;
    @InjectView(R.id.dashboard_sensors) Button sensorsButton;
    @InjectView(R.id.heat_map_button_container) View mapContainer;
    @InjectView(R.id.heat_map_button) View mapButton;
    @InjectView(R.id.graph_button_container) View graphContainer;
    @InjectView(R.id.graph_button) View graphButton;

    private StreamAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intents.startDatabaseWriterService(context);
        setContentView(R.layout.dashboard);
        adapter = adapterFactory.getAdapter(this);
        microphoneButton.setOnClickListener(this);
        sensorsButton.setOnClickListener(this);

        initToolbar("Dashboard");
        initNavigationDrawer();
    }

    @Override
    public void onStart() {
        super.onStart();
        getDelegate().onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intents.startDatabaseWriterService(context);

        adapter.start();
        adapter.notifyDataSetChanged();

        startSensors(context);
    }

    @Override
    protected void onPause() {
        super.onPause();

        adapter.stop();
        stopSensors(context);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.graph_button:
                if (adapter.getCount() == 1) {
                    startActivity(new Intent(this, GraphActivity.class));
                } else {
                    Toast.makeText(this, R.string.drag_to_graph_stream, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.heat_map_button:
                if (adapter.getCount() == 1) {
                    if (sessionManager.isLocationless()) {
                        Toast.makeText(DashboardActivity.this, R.string.cant_map_without_gps, Toast.LENGTH_SHORT).show();
                    } else {
                        context.startActivity(new Intent(DashboardActivity.this, AirCastingMapActivity.class));
                    }
                } else {
                    Toast.makeText(context, R.string.drag_to_map_stream, Toast.LENGTH_LONG).show();
                }
                break;
            case R.id.dashboard_microphone:
                return;
            case R.id.dashboard_sensors:
                startActivity(new Intent(this, ExternalSensorActivity.class));
                break;
        }
        super.onClick(view);
    }

    @Override
    public void onProfileClick(View view) {
        super.onProfileClick(view);
    }
}