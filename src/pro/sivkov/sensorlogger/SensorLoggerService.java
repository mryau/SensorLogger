package pro.sivkov.sensorlogger;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SensorLoggerService extends Service implements LocationListener {

	public final static String TICK_INTERFAL = "TickInterval";
	public final static String TICK_SERVER = "TickServer";
	private final static String TAG = "TickService";
	private final static Object mLock = new Object();
	// time to sleep between server exchange ticks
	private int mTts;
	private String mUrl;
	private Location mLastLocationReading;
	private LocationManager mLocationManager;
	// default minimum time between new readings in ms
	private long mMinTime = 5000;
	// default minimum distance between old and new readings in meters.
	private float mMinDistance = 3.0f;
	private Thread mWorkThread;
	private boolean mWorkThreadRunning = false;
	private boolean mSendLocation = false;
	private String mDevId;

	public void onCreate() {
		super.onCreate();
		if (null == (mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE))) {
			Log.d(TAG, "onCreate: can't get LocationManager");
			stopSelf();
		} else {
			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			mDevId = telephonyManager.getDeviceId();
			if (mDevId == null)
				mDevId = "0000";

			mLastLocationReading = bestLastKnownLocation(mMinTime, mMinDistance);

			if (mLastLocationReading != null)
					mSendLocation = true;

			// Register for network location updates
			boolean network_enabled = mLocationManager
					.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
			boolean gps_enabled = mLocationManager
					.isProviderEnabled(LocationManager.GPS_PROVIDER);
			if (network_enabled) {
				mLocationManager.requestLocationUpdates(
						LocationManager.NETWORK_PROVIDER, mMinTime,
						mMinDistance, this);
				Log.d(TAG, "registered to NETWORK_PROVIDER LocationUpdates");
			}
			if (gps_enabled)
				mLocationManager.requestLocationUpdates(
						LocationManager.GPS_PROVIDER, mMinTime, mMinDistance,
						this);
			Log.d(TAG, "registered to GPS_PROVIDER LocationUpdates");
		}
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		mTts = intent.getIntExtra(TICK_INTERFAL, 15);
		mUrl = intent.getStringExtra(TICK_SERVER);
		if (mUrl == null)
			mUrl = "http://localhost:8080/ticks";
		Log.d(TAG, "onStartCommand");
		someTask();
		return super.onStartCommand(intent, flags, startId);
	}

	public void onDestroy() {
		super.onDestroy();
		mLocationManager.removeUpdates(this);
		mWorkThreadRunning = false;
		mWorkThread.interrupt();
		Log.d(TAG, "onDestroy");
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind");
		return null;
	}

	protected String getCurrentLocation() {
		if (mLastLocationReading != null)
			return "lat=" + mLastLocationReading.getLatitude() + "&lng="
					+ mLastLocationReading.getLongitude() + "&alt="
					+ mLastLocationReading.getAltitude() + "&devId=" + mDevId;
		else
			return "unknown location";
	}

	void sendLocationToServer() {
		if (false == mWorkThreadRunning)
			return;
		synchronized (mLock) {
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPut put = new HttpPut(mUrl);
				put.addHeader("Content-Type", "application/json");
				put.addHeader("Accept", "application/json");
				JSONObject jsonObj = new JSONObject();
				jsonObj.put("lat",
						mLastLocationReading.getLatitude());
				jsonObj.put("lng",
						mLastLocationReading.getLongitude());
				jsonObj.put("alt",
						mLastLocationReading.getAltitude());
				jsonObj.put("dev", mDevId);
	
				put.setEntity(new StringEntity(jsonObj.toString()));
				HttpResponse response = client.execute(put);
				mSendLocation = false;
				Log.d(TAG, getCurrentLocation());
			} catch (Exception e) {
				Log.d(TAG, "sendLocationToServer: "+e.getMessage());
			}
		}
	}

	void someTask() {
		mWorkThread = new Thread(new Runnable() {
			public void run() {
				while (mWorkThreadRunning) {
					if (mSendLocation && mLastLocationReading != null)
						sendLocationToServer();
					try {
						TimeUnit.SECONDS.sleep(mTts);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		mWorkThreadRunning = true;
		mWorkThread.start();
	}

	// Get the last known location from all providers
	// return best reading is as accurate as minAccuracy and
	// was taken no longer then minTime milliseconds ago

	private Location bestLastKnownLocation(long minTime, float minAccuracy) {

		Location bestResult = null;
		float bestAccuracy = Float.MAX_VALUE;
		long bestTime = Long.MIN_VALUE;

		List<String> matchingProviders = mLocationManager.getAllProviders();

		for (String provider : matchingProviders) {

			Location location = mLocationManager.getLastKnownLocation(provider);

			if (location != null) {

				float accuracy = location.getAccuracy();
				long time = location.getTime();

				if (accuracy < bestAccuracy) {

					bestResult = location;
					bestAccuracy = accuracy;
					bestTime = time;

				}
			}
		}

		// Return best reading or null
		if (bestAccuracy > minAccuracy || bestTime < minTime) {
			return null;
		} else {
			return bestResult;
		}
	}

	private long age(Location location) {
		return System.currentTimeMillis() - location.getTime();
	}

	@Override
	public void onLocationChanged(Location currentLocation) {
		// 1) If there is no last location, keep the current location.
		if (mLastLocationReading == null) {
			mLastLocationReading = currentLocation;
			mSendLocation = true;
		}
		// 2) If the current location is older than the last location, ignore
		// the current location
		else if (age(currentLocation) > age(mLastLocationReading)) {
			;// pass
		}
		// 3) If the current location is newer than the last locations, keep the
		// current location.
		else if (age(currentLocation) < age(mLastLocationReading)
				&& mLastLocationReading.getLatitude() == currentLocation
						.getLatitude()
				&& mLastLocationReading.getLongitude() == currentLocation
						.getLongitude()) {
			synchronized (mLock) {
				mLastLocationReading = currentLocation;
				mSendLocation = true;
			}
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {
		Log.d(TAG, "onProviderDisabled");
	}

	@Override
	public void onProviderEnabled(String arg0) {
		Log.d(TAG, "onProviderEnabled");
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
		Log.d(TAG, "onStatusChanged");
	}

}
