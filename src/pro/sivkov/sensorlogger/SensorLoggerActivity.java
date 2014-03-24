package pro.sivkov.sensorlogger;

import pro.sivkov.sensorlogger.R;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.SyncStateContract.Constants;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class SensorLoggerActivity extends Activity {

	private static final int[] intervals = { 15, 30, 60, 300 };
	private static final String[] intervalNames = { "15s", "30s", "1m", "5m" };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		final ListView intervalsLV = (ListView) findViewById(R.id.intervals_lv);
	    intervalsLV.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, intervalNames);
	    intervalsLV.setAdapter(adapter);
	    intervalsLV.setItemChecked(0, true);

	    // Repeating Alarm Button
		final Button startLoggerButton = (Button) findViewById(R.id.start_logger_button);
		startLoggerButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (checkIfNetworkLocationAvailable(SensorLoggerActivity.this))
					startTickService();
			}
		});

		// Cancel Repeating Alarm Button
		final Button stopLoggerButton = (Button) findViewById(R.id.stop_logger_button);
		stopLoggerButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				stopService(new Intent(SensorLoggerActivity.this, SensorLoggerService.class));

				Toast.makeText(getApplicationContext(),
						"TickService Cancelled", Toast.LENGTH_LONG).show();
			}
		});

	}
	
	protected void startTickService() {
		final Intent intent = new Intent(SensorLoggerActivity.this, SensorLoggerService.class);
		stopService(intent);
		startService(intent);
		intent.putExtra(SensorLoggerService.TICK_INTERFAL, getSelectedInterval());
		String msg = "TickService will send location date every " + getSelectedIntervalDescription()
				+ " to "+ getServerUrl();
		Toast.makeText(getApplicationContext(), msg,
				Toast.LENGTH_LONG).show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		startTickService();
	}

	private String getServerUrl() {
		final TextView serverUriTV = (TextView) findViewById(R.id.server_address);
		String serverUri = serverUriTV.getText().toString();
		return serverUri.length() < 6 ? SensorLoggerService.TICK_SERVER : serverUri;
	}

	private boolean checkIfNetworkLocationAvailable(Context context) {

		LocationManager locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		boolean network_enabled = locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
		boolean gps_enabled=locManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		if (network_enabled && gps_enabled)
			return true;
		if (false == (network_enabled && gps_enabled)) {
	        //show dialog to allow user to enable location settings
	        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
	        dialog.setTitle(context.getString(R.string.location_services_dlg_title));
	        dialog.setMessage(R.string.location_services_disabled);

	        dialog.setPositiveButton(context.getString(R.string.yes), new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int which) {
	                startActivityForResult(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0);
	            }
	        });

	        dialog.setNegativeButton(context.getString(R.string.no), new DialogInterface.OnClickListener() {

	            public void onClick(DialogInterface dialog, int which) {
	                finish();
	            }
	        });

	        dialog.show();
	    }
		return false;

	}
	
	protected int getSelectedIntervalPos() {
		final ListView intervalsLV = (ListView) findViewById(R.id.intervals_lv);
		int pos =  intervalsLV.getSelectedItemPosition();
		if (pos == ListView.INVALID_POSITION)
			pos = 0;
		return pos;
		
	}

	protected int getSelectedInterval() {
		return intervals[getSelectedIntervalPos()];
		
	}

	protected String getSelectedIntervalDescription() {
		return intervalNames[getSelectedIntervalPos()];
		
	}

}