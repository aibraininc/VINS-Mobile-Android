package com.aibrain.tyche.bluetoothle.activity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.aibrain.tyche.bluetoothle.BluetoothLeService;
import com.aibrain.tyche.bluetoothlelib.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class BluetoothLeScanActivity extends Activity implements GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener,
		Animation.AnimationListener {

	private static final String TAG = BluetoothLeScanActivity.class.getSimpleName();
	private static final boolean DBG = false;
	
	private static final int SCAN_DURATION = 3000;
	private static final int ANIMATION_DURATION = 1000;
	
	private static final String TYCHE = "TYCHE";
	private static final int REQUEST_ENABLE_BT = 1;
	private static final int MY_PERMISSIONS_REQUEST_BLE_SEARCH = 2;
	
	public static final String ACTION_TYCHE_FOUND = 
			"com.aibrain.tyche.bluetoothle.ACTION_TYCHE_FOUND";
	public static final String ACTION_TYCHE_NOT_FOUND = 
			"com.aibrain.tyche.bluetoothle.ACTION_TYCHE_NOT_FOUND";
	public static final String ACTION_BLUETOOTH_NOT_SUPPORTED = 
			"com.aibrain.tyche.bluetoothle.ACTION_BLUETOOTH_NOT_SUPPORTED";
	public static final String EXTRA_TYCHE_DEVICE_ADDRESS = 
			"com.aibrain.tyche.bluetoothle.EXTRA_TYCHE_DEVICE_ADDRESS";
	public static final String EXTRA_TYCHE_DEVICE_NAME = 
			"com.aibrain.tyche.bluetoothle.ACTION_TYCHE_DEVICE_NAME";
	
	private Handler mHandler = new Handler();
	private BluetoothAdapter mBluetoothAdapter;
	
	private TextView mStatusTv;
	private boolean mIsScanning = false;
	private boolean mIsFound = false;
	private boolean mIsSelected = false;

	private Button mScanButton;
	private Button mEnableButton;

	private RecyclerView mDevicesRecyclerView;
	private LeDeviceListAdapter mLeDeviceListAdapter;

	private GoogleApiClient mGoogleApiClient;
	private final static int REQUEST_ENABLE_LOCATION = 2;

	private Animation mFadeInAnimation;
	private Animation mFadeIn1CancelableAnim;
	private Animation mFadeIn2CancelableAnim;
	private Animation mFadeOutCancelableAnim;

	private TextView mTvDescription1;
	private TextView mTvDescription2;

	private BroadcastReceiver mTycheConnStatusReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(BluetoothLeService.ACTION_TYCHE_CONNECTED.equals(action)) {
				if(DBG) Log.d(TAG, "#BroadcastReceiver: ACTION_TYCHE_CONNECTED");

				startFadeInAnimByStatusView(R.string.txt_conn_success);
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						finish();
					}
				}, 1000);
			}
			else if(BluetoothLeService.ACTION_TYCHE_DISCONNECTED.equals(action)) {
				if(DBG) Log.d(TAG, "#BroadcastReceiver: ACTION_TYCHE_DISCONNECTED");

				startFadeInAnimByStatusView(R.string.txt_conn_fail);
				mScanButton.setText(R.string.txt_try_again);
				mScanButton.setVisibility(View.VISIBLE);
				mScanButton.startAnimation(mFadeInAnimation);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(R.style.ScanActivityTheme);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_bluetooth_le_scan_list);

		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
				!= PackageManager.PERMISSION_GRANTED) {

			ActivityCompat.requestPermissions(this,
					new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_BLE_SEARCH);
		}
		else {
			initUI();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_TYCHE_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_TYCHE_DISCONNECTED);
		registerReceiver(mTycheConnStatusReceiver, intentFilter);
	}

	@Override
	protected void onPause() {
		super.onPause();

		unregisterReceiver(mTycheConnStatusReceiver);
	}

	private boolean isLocationEnabled() {
		int locationMode;
		String locationProviders;

		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			try {
				locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

			} catch (Settings.SettingNotFoundException e) {
				return false;
			}

			return locationMode != Settings.Secure.LOCATION_MODE_OFF;

		}
		else {
			locationProviders = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
			return !TextUtils.isEmpty(locationProviders);
		}


	}
	private void requestLocationService() {
		// If location was diabled, it is requested to enable.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			mGoogleApiClient = new GoogleApiClient.Builder(BluetoothLeScanActivity.this)
					.addApi(LocationServices.API)
					.addConnectionCallbacks(BluetoothLeScanActivity.this)
					.addOnConnectionFailedListener(BluetoothLeScanActivity.this)
					.build();

			mGoogleApiClient.connect();
		}
	}

	private void requestBluetooth() {
		Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(intent, REQUEST_ENABLE_BT);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch(requestCode) {
			case MY_PERMISSIONS_REQUEST_BLE_SEARCH:
				if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					initUI();
				}
				else {
					finish();
				}
		}
	}
	
	private void initUI() {
		mFadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
		mFadeInAnimation.setDuration(ANIMATION_DURATION);
			
		mStatusTv = (TextView)findViewById(R.id.tv_scan_status);
		
		mScanButton = (Button)findViewById(R.id.bt_scan);
		mScanButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				mScanButton.clearAnimation();
				scan();
			}
		});

		mEnableButton = (Button)findViewById(R.id.bt_enable);
		mEnableButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				if(!mBluetoothAdapter.isEnabled()) {
					requestBluetooth();
				}
				else {
					requestLocationService();
				}
			}
		});


		final ImageButton cancelButton = (ImageButton)findViewById(R.id.bt_cancel);
		cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				finish();
			}
		});

		mDevicesRecyclerView = (RecyclerView)findViewById(R.id.recyclerView_devices);
		mDevicesRecyclerView.setHasFixedSize(true);
		LinearLayoutManager layoutManager = new LinearLayoutManager(this);
		layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
		mDevicesRecyclerView.setLayoutManager(layoutManager);

		mLeDeviceListAdapter = new LeDeviceListAdapter();
		mDevicesRecyclerView.setAdapter(mLeDeviceListAdapter);

		mTvDescription1 = (TextView)findViewById(R.id.tv_description1);
		mTvDescription2 = (TextView)findViewById(R.id.tv_description2);

		initAnimation();

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				mTvDescription1.startAnimation(mFadeIn1CancelableAnim);
			}
		}, 1000);
	}



	private void initAnimation() {
		mFadeIn1CancelableAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
		mFadeIn1CancelableAnim.setDuration(1500);
		mFadeIn1CancelableAnim.setFillAfter(true);
		mFadeIn1CancelableAnim.setAnimationListener(this);

		mFadeIn2CancelableAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
		mFadeIn2CancelableAnim.setDuration(1500);
		mFadeIn2CancelableAnim.setFillAfter(true);
		mFadeIn2CancelableAnim.setAnimationListener(this);

		mFadeOutCancelableAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
		mFadeOutCancelableAnim.setDuration(1500);
		mFadeOutCancelableAnim.setFillAfter(true);
		mFadeOutCancelableAnim.setAnimationListener(this);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if(event.getAction() == MotionEvent.ACTION_UP) {
			if (mFadeIn1CancelableAnim.hasStarted() && !mFadeIn1CancelableAnim.hasEnded()) {
				mTvDescription1.clearAnimation();
				mTvDescription1.setVisibility(View.VISIBLE);
			} else if (mFadeIn2CancelableAnim.hasStarted() && !mFadeIn2CancelableAnim.hasEnded()) {
				mTvDescription2.clearAnimation();
				mTvDescription2.setVisibility(View.VISIBLE);
			} else if (mFadeOutCancelableAnim.hasStarted() && !mFadeOutCancelableAnim.hasEnded()) {
				mTvDescription1.clearAnimation();
				mTvDescription2.clearAnimation();
				mTvDescription1.setVisibility(View.INVISIBLE);
				mTvDescription2.setVisibility(View.INVISIBLE);
			}
		}

		return super.onTouchEvent(event);
	}

	@Override
	protected void onDestroy() {
		if(mBluetoothAdapter != null && mIsScanning) {
			mIsScanning = false;
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			if(DBG) Log.d(TAG, "Scan canceled");
		}

		if(!mIsSelected) {
			Intent intent = new Intent(ACTION_TYCHE_NOT_FOUND);
			sendBroadcast(intent);
		}
		
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_ENABLE_BT) {
			switch(resultCode) {
				case Activity.RESULT_OK:
					if(isLocationEnabled()) {
						//mScanButton.setVisibility(View.VISIBLE);
						mEnableButton.setVisibility(View.INVISIBLE);
						scan();
					}
					else {
						requestLocationService();
					}
					break;
				case Activity.RESULT_CANCELED:
					mScanButton.setVisibility(View.INVISIBLE);
					mEnableButton.setVisibility(View.VISIBLE);
					startFadeInAnimByStatusView(R.string.txt_bluetooth_not_enabled);
					mEnableButton.startAnimation(mFadeInAnimation);
					break;
			}
		}
		else if(requestCode == REQUEST_ENABLE_LOCATION) {
			switch (resultCode)
			{
				case Activity.RESULT_OK:
				{
					// All required changes were successfully made
					//mScanButton.setVisibility(View.VISIBLE);
					mEnableButton.setVisibility(View.INVISIBLE);
					scan();
					break;
				}
				case Activity.RESULT_CANCELED:
				{
					// The user was asked to change settings, but chose not to
					mScanButton.setVisibility(View.INVISIBLE);
					mEnableButton.setVisibility(View.VISIBLE);
					startFadeInAnimByStatusView(R.string.txt_location_needed);
					mEnableButton.startAnimation(mFadeInAnimation);
					break;
				}
				default:
				{
					break;
				}
			}
		}
		
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void scan() {
		if(mBluetoothAdapter == null) {
			return;
		}

		mLeDeviceListAdapter.removeAll();
		mLeDeviceListAdapter.notifyDataSetChanged();

		String msg = checkLeStatus();
		if(msg != null) {
			mScanButton.setVisibility(View.INVISIBLE);
			mEnableButton.setVisibility(View.VISIBLE);
			startFadeInAnimByStatusView(msg);
			mEnableButton.startAnimation(mFadeInAnimation);

			return;
		}

		mIsSelected = false;
		mIsFound = false;
		mIsScanning = true;
		mScanButton.setVisibility(View.INVISIBLE);

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if(mIsScanning) {
					mIsScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					mScanButton.setText(R.string.txt_re_scan);
					mScanButton.setVisibility(View.VISIBLE);
					//mScanButton.startAnimation(mFadeInAnimation);

					if(mIsFound) {
						if (DBG) Log.d(TAG, "Scan finished!");
						startFadeInAnimByStatusView(R.string.txt_select_tyche);
						mDevicesRecyclerView.setVisibility(View.VISIBLE);
						mLeDeviceListAdapter.notifyDataSetChanged();
					}
					else {
						if (DBG) Log.d(TAG, "Scan failed!");
						startFadeInAnimByStatusView(R.string.txt_tyche_not_found);
						mDevicesRecyclerView.setVisibility(View.GONE);
					}
					
					/*
					Intent intent = new Intent(ACTION_TYCHE_NOT_FOUND);
					sendBroadcast(intent);
					*/
				}
			}
		}, SCAN_DURATION);

		mBluetoothAdapter.startLeScan(mLeScanCallback);
		if(DBG) Log.d(TAG, "Scan started");
		startFadeInAnimByStatusView(R.string.txt_scanning);
	}

	private String checkLeStatus() {
		String resultMsg = null;
		
		if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			resultMsg = getString(R.string.txt_ble_not_supported);
			if(DBG) Log.d(TAG, resultMsg);
			return resultMsg;
		}
		 
		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(bluetoothManager == null) {
			resultMsg = getString(R.string.txt_get_service_failed);
			if(DBG) Log.d(TAG, resultMsg);
			return resultMsg;
		}
		
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if(mBluetoothAdapter == null) {
			resultMsg = getString(R.string.txt_bluetooth_not_supported);
			if(DBG) Log.d(TAG, resultMsg);
			return resultMsg;
		}

		if(!mBluetoothAdapter.isEnabled()) {
			resultMsg = getString(R.string.txt_bluetooth_not_enabled);
			requestBluetooth();
			return resultMsg;
		}
		else {
			if(!isLocationEnabled()) {
				resultMsg = getString(R.string.txt_location_needed);
				requestLocationService();
				return resultMsg;
			}
		}

		return resultMsg;
	}
	
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
			if(device.getName() == null) {
				return;
			}
			
			if(DBG) Log.d(TAG, "(" + device.getAddress() + ", " + device.getName() + ")");

			if(device.getName().contains(TYCHE)) {
				runOnUiThread(new Runnable() {
					public void run() {
						StringTokenizer st = new StringTokenizer(device.getAddress(), ":");
						if(st.hasMoreTokens()) {
							if(st.nextToken().equals("00")) {
								mLeDeviceListAdapter.addDevice(device);
								mIsFound = true;
							}
						}
					}
				});
			}
		}
		
	};

	private void startFadeInAnimByStatusView(CharSequence text) {
		mStatusTv.setText(text);
		mStatusTv.startAnimation(mFadeInAnimation);
	}
	
	private void startFadeInAnimByStatusView(int id) {
		mStatusTv.setText(getString(id));
		mStatusTv.startAnimation(mFadeInAnimation);
	}

	@Override
	public void onAnimationStart(Animation animation) {

	}

	@Override
	public void onAnimationEnd(Animation animation) {
		if(animation.equals(mFadeIn1CancelableAnim)) {
			mTvDescription2.startAnimation(mFadeIn2CancelableAnim);
		}
		else if(animation.equals(mFadeIn2CancelableAnim)) {
			mTvDescription1.startAnimation(mFadeOutCancelableAnim);
			mTvDescription2.startAnimation(mFadeOutCancelableAnim);
		}
		else if(animation.equals(mFadeOutCancelableAnim)) {
			mStatusTv.setVisibility(View.VISIBLE);
			String msg = checkLeStatus();
			if(msg == null) {
				//mScanButton.setVisibility(View.VISIBLE);
				mEnableButton.setVisibility(View.INVISIBLE);
				scan();
			}
			else {
				startFadeInAnimByStatusView(msg);
			}
		}
	}

	@Override
	public void onAnimationRepeat(Animation animation) {

	}

	private class LeDeviceListAdapter extends RecyclerView.Adapter<LeDeviceListAdapter.LeDeviceViewHolder> {

		private ArrayList<BluetoothDevice> devices;

		public LeDeviceListAdapter() {
			devices = new ArrayList<>();
		}

		@Override
		public int getItemCount() {
			return devices.size();
		}

		@Override
		public LeDeviceViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
			View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.list_item_device, viewGroup, false);

			return new LeDeviceViewHolder(itemView);
		}

		@Override
		public void onBindViewHolder(LeDeviceViewHolder deviceViewHolder, int i) {
			final BluetoothDevice device  = devices.get(i);

			// card view
			deviceViewHolder.cardView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					//connection
					if (device == null) return;

					Intent intent = new Intent(ACTION_TYCHE_FOUND);
					intent.putExtra(EXTRA_TYCHE_DEVICE_ADDRESS, device.getAddress());
					intent.putExtra(EXTRA_TYCHE_DEVICE_NAME, device.getName());
					sendBroadcast(intent);
					mIsSelected = true;

					startFadeInAnimByStatusView(R.string.txt_connecting);
					mScanButton.setVisibility(View.INVISIBLE);
					mDevicesRecyclerView.setVisibility(View.GONE);
					//finish();
				}
			});
			deviceViewHolder.cardView.setBackground(getResources().getDrawable(R.drawable.selector_custom_btn));

			// device name
			deviceViewHolder.tvDeviceName.setText(device.getName());

			// device address
			deviceViewHolder.tvDeviceAddress.setText(device.getAddress());
		}

		public void addDevice(BluetoothDevice device) {
			if(!devices.contains(device)) {
				devices.add(device);
			}
		}

		public void removeAll() {
			devices.clear();
		}

		public BluetoothDevice getDevice(int position) {
			return devices.get(position);
		}

		public class LeDeviceViewHolder extends RecyclerView.ViewHolder {

			protected CardView cardView;
			protected TextView tvDeviceName;
			protected TextView tvDeviceAddress;

			public LeDeviceViewHolder(View itemView) {
				super(itemView);

				cardView = (CardView)itemView;
				tvDeviceName = (TextView)itemView.findViewById(R.id.app_name);
				tvDeviceAddress = (TextView)itemView.findViewById(R.id.app_description);
			}
		}

	}

	@Override
	public void onConnected(Bundle bundle) {
		LocationRequest locationRequest = LocationRequest.create();
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setInterval(10000);
		locationRequest.setFastestInterval(5000);

		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
				.addLocationRequest(locationRequest);
		builder.setAlwaysShow(true);
		PendingResult<LocationSettingsResult> result
				= LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

		result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
			@Override
			public void onResult(LocationSettingsResult result) {
				final Status status = result.getStatus();
				//final LocationSettingsStates state = result.getLocationSettingsStates();
				switch (status.getStatusCode()) {
					case LocationSettingsStatusCodes.SUCCESS:
						// All location settings are satisfied. The client can initialize location
						// requests here.
						//...
						break;
					case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
						// Location settings are not satisfied. But could be fixed by showing the user
						// a dialog.
						try {
							// Show the dialog by calling startResolutionForResult(),
							// and check the result in onActivityResult().
							status.startResolutionForResult(
									BluetoothLeScanActivity.this,
									REQUEST_ENABLE_LOCATION);
						} catch (IntentSender.SendIntentException e) {
							// Ignore the error.
						}
						break;
					case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
						// Location settings are not satisfied. However, we have no way to fix the
						// settings so we won't show the dialog.
						//...
						break;
				}
			}
		});

	}

	@Override
	public void onConnectionSuspended(int i) {

	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

	}
	
}
