/**	 Cycle Altanta, Copyright 2012 Georgia Institute of Technology
 *                                    Atlanta, GA. USA
 *
 *   @author Christopher Le Dantec <ledantec@gatech.edu>
 *   @author Anhong Guo <guoanhong15@gmail.com>
 *
 *   Updated/Modified for Atlanta's app deployment. Based on the
 *   CycleTracks codebase for SFCTA.
 *
 *   CycleTracks, Copyright 2009,2010 San Francisco County Transportation Authority
 *                                    San Francisco, CA, USA
 *
 * 	 @author Billy Charlton <billy.charlton@sfcta.org>
 *
 *   This file is part of CycleTracks.
 *
 *   CycleTracks is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   CycleTracks is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.gatech.ppl.cycleatlanta;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.provider.Settings;

public class RecordingService extends Service implements LocationListener, SensorEventListener {
	FragmentMainInput recordActivity;
	LocationManager lm = null;
	SensorManager sm = null;
	Sensor magSensor = null;
	DbAdapter mDb;

	// Bike bell variables
	static int BELL_FIRST_INTERVAL = 20;
	static int BELL_NEXT_INTERVAL = 5;
	Timer timer;
	SoundPool soundpool;
	int bikebell;

	Vibrator vibe;
	MediaPlayer mp;

	final Handler mHandler = new Handler();
	final Runnable mRemindUser = new Runnable() {
		public void run() {
			remindUser();
		}
	};

	// Aspects of the currently recording trip
	double latestUpdate;
	double lastMag = 0;
	double lastBumpRecord = 0;
	float lastValue = 0;
	double lastCal = 0;
	double magStartTime = 0;
	boolean isBump = false;
	int numBumps = 0;

	Location lastLocation;
	float distanceTraveled;
	float curSpeed, maxSpeed;
	TripData trip;

	public final static int STATE_IDLE = 0;
	public final static int STATE_RECORDING = 1;
	public final static int STATE_PAUSED = 2;
	public final static int STATE_FULL = 3;

	int state = STATE_IDLE;
	private final MyServiceBinder myServiceBinder = new MyServiceBinder();

	// ---SERVICE methods - required! -----------------
	@Override
	public IBinder onBind(Intent arg0) {
		return myServiceBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		soundpool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
		bikebell = soundpool.load(this.getBaseContext(), R.raw.bikebell, 1);

		vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		mp = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
	}

	public class MyServiceBinder extends Binder implements IRecordService {
		public int getState() {
			return state;
		}

		public void startRecording(TripData trip) {
			RecordingService.this.startRecording(trip);
		}

		public void cancelRecording() {
			RecordingService.this.cancelRecording();
		}

		public void pauseRecording() {
			RecordingService.this.pauseRecording();
		}

		public void resumeRecording() {
			RecordingService.this.resumeRecording();
		}

		public long finishRecording() {
			return RecordingService.this.finishRecording();
		}

		public long getCurrentTrip() {
			if (RecordingService.this.trip != null) {
				return RecordingService.this.trip.tripid;
			}
			return -1;
		}

		public void reset() {
			RecordingService.this.state = STATE_IDLE;
		}

		public void setListener(FragmentMainInput mia) {
			RecordingService.this.recordActivity = mia;
			notifyListeners();
		}
	}

	// ---end SERVICE methods -------------------------

	public void startRecording(TripData trip) {
		this.state = STATE_RECORDING;
		this.trip = trip;

		curSpeed = maxSpeed = distanceTraveled = 0.0f;
		lastLocation = null;

		numBumps = 0;

		// Add the notify bar and blinking light
		setNotification();

		// Start listening for GPS updates!
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);

		// Start listening for Magnet sensor
		sm = (SensorManager) getSystemService(SENSOR_SERVICE);
		magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_FASTEST);

		// Set up timer for bike bell
		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				mHandler.post(mRemindUser);
			}
		}, BELL_FIRST_INTERVAL * 60000, BELL_NEXT_INTERVAL * 60000);
	}

	public void pauseRecording() {
		this.state = STATE_PAUSED;
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.removeUpdates(this);
		
		sm = (SensorManager)getSystemService(SENSOR_SERVICE);
		sm.unregisterListener(this);
	}

	public void resumeRecording() {
		this.state = STATE_RECORDING;
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
		
		sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sm.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_FASTEST);
	}

	public long finishRecording() {
		this.state = STATE_FULL;
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.removeUpdates(this);
		
		sm = (SensorManager)getSystemService(SENSOR_SERVICE);
		sm.unregisterListener(this);

		clearNotifications();

		return trip.tripid;
	}

	public void cancelRecording() {
		if (trip != null) {
			trip.dropTrip();
		}

		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.removeUpdates(this);
		
		sm = (SensorManager)getSystemService(SENSOR_SERVICE);
		sm.unregisterListener(this);

		clearNotifications();
		this.state = STATE_IDLE;
	}

	public void registerUpdates(FragmentMainInput r) {
		this.recordActivity = r;
	}

	public TripData getCurrentTrip() {
		return trip;
	}

	// LocationListener implementation:
	@Override
	public void onLocationChanged(Location loc) {
		if (loc != null) {
			// Only save one beep per second.
			double currentTime = System.currentTimeMillis();
			if (currentTime - latestUpdate > 999) {

				latestUpdate = currentTime;
				updateTripStats(loc);
				boolean rtn = trip.addPointNow(loc, currentTime,
						distanceTraveled);
				// Log.v("Jason", "Distance Traveled: " + distanceTraveled);
				if (!rtn) {
					// Log.e("FAIL", "Couldn't write to DB");
				}

				// Update the status page every time, if we can.
				notifyListeners();
			}
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {
	}

	@Override
	public void onProviderEnabled(String arg0) {
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
	}

	// END LocationListener implementation:

	public void remindUser() {
		soundpool.play(bikebell, 1.0f, 1.0f, 1, 0, 1.0f);

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.icon48;
		long when = System.currentTimeMillis();
		int minutes = (int) (when - trip.startTime) / 60000;
		CharSequence tickerText = String.format("Still recording (%d min)",
				minutes);

		Notification notification = new Notification(icon, tickerText, when);
		notification.flags |= Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_SHOW_LIGHTS;
		notification.ledARGB = 0xffff00ff;
		notification.ledOnMS = 300;
		notification.ledOffMS = 3000;

		Context context = this;
		CharSequence contentTitle = "Cycle Atlanta - Recording";
		CharSequence contentText = "Tap to see your ongoing trip";
		Intent notificationIntent = new Intent(context, FragmentMainInput.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);
		final int RECORDING_ID = 1;
		mNotificationManager.notify(RECORDING_ID, notification);
	}

	private void setNotification() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.icon36;
		CharSequence tickerText = "Recording...";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		notification.ledARGB = 0xffff00ff;
		notification.ledOnMS = 300;
		notification.ledOffMS = 3000;
		notification.flags = notification.flags
				| Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_INSISTENT
				| Notification.FLAG_NO_CLEAR;

		Context context = this;
		CharSequence contentTitle = "Cycle Atlanta - Recording";
		CharSequence contentText = "Tap to see your ongoing trip";
		Intent notificationIntent = new Intent(context, TabsConfig.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
				| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);

		// notification.flags |= Notification.FLAG_AUTO_CANCEL;
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		final int RECORDING_ID = 1;
		mNotificationManager.notify(RECORDING_ID, notification);
	}

	private void clearNotifications() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancelAll();

		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
	}

	private void updateTripStats(Location newLocation) {
		final float spdConvert = 2.2369f;

		// Stats should only be updated if accuracy is decent
		if (newLocation.getAccuracy() < 20) {
			// Speed data is sometimes awful, too:
			curSpeed = newLocation.getSpeed() * spdConvert;
			if (curSpeed < 60.0f) {
				maxSpeed = Math.max(maxSpeed, curSpeed);
			}
			if (lastLocation != null) {
				float segmentDistance = lastLocation.distanceTo(newLocation);
				distanceTraveled = distanceTraveled + segmentDistance;
			}

			lastLocation = newLocation;
		}
	}

	void notifyListeners() {
		if (recordActivity != null) {
			// Log.v("Jason", "Distance Traveled: hahaha");
			recordActivity.updateStatus(trip.numpoints, distanceTraveled,
					curSpeed, maxSpeed);
		}
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		float x = event.values[0];
		float y =  event.values[1];
		float z = event.values[2];
		float value = Math.abs(x)+Math.abs(y)+Math.abs(z);
		double cal = Math.sqrt(x*x+y*y+z*z);
		double currentTime = System.currentTimeMillis();

		// first time
		if(lastCal == 0 && lastMag == 0){
			magStartTime = currentTime;
			lastValue = value;
			lastMag = currentTime;		//last calibration time
			lastBumpRecord = currentTime;	//last bump recording time
			lastCal = cal;		//calibration
		}

		// first 5 seconds calibrate
		if(currentTime - magStartTime < 5000){
			lastCal = (lastCal+cal)/2;
			lastMag = currentTime;
			lastValue = value;
			return;
		}

		//TODO: it's bump
		if(currentTime - lastBumpRecord > 999){
			if(cal > 2 * lastCal){
				if(!isBump){
					isBump = true;
					//if(currentTime - lastBumpRecord > 2100){
					lastBumpRecord = currentTime;
					numBumps++;
					vibe.vibrate(500);
					mp.start();

					boolean rtn = true;
					if(lastLocation != null){
						//add specific note with "mag" flag
						//rtn = trip.addFlagNow(lastLocation, currentTime);
					}
					else{
						// TO BE REMOVED
						lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
						Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
						//add specific note with "mag" flag
						//rtn = trip.addFlagNow(location, currentTime);
						//System.out.println("#########"+location.getLatitude()+"     "+location.getLongitude());
					}
					if (!rtn) {
						System.out.println("could not save flag!!####!!!###");
					}

					// Update the status page every time, if we can.
					notifyListeners();
					//}
				}
			}
			else if(cal < 1.5 * lastCal){
				isBump = false;
			}
		}

		//calibrate each second
		if(currentTime - lastMag > 999){
			if(!isBump){
				lastCal = (lastCal+cal)/2;
				lastMag = currentTime;
			}
		}

 		//Location lastLocation;
		//TripData trip;
		//double currentTime = System.currentTimeMillis();

	}
}
