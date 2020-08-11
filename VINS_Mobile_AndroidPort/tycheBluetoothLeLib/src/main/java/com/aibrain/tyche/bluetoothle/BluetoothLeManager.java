package com.aibrain.tyche.bluetoothle;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.aibrain.tyche.bluetoothle.BluetoothLeService.BluetoothLeServiceCallback;
import com.aibrain.tyche.bluetoothle.activity.BluetoothLeScanActivity;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

import java.util.ArrayList;

public class BluetoothLeManager {

	private static final String TAG = BluetoothLeManager.class.getSimpleName();
	private static final boolean DBG = false;
	
	private static BluetoothLeManager sInstance;

	private BluetoothLeService mBluetoothLeService;
	
	private String mTycheAddress;
	private Boolean mIsReceiverRegistered = false;

	private int mMinBattVal = 255;
	
	public interface Callback {
		void onTycheFound(String address, String name);
		void onTycheNotFound();
		void onTycheConnect();
		void onTycheDisconnect();
		void onReceive(StatusData status);
	}
	private ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
	
	private BluetoothLeServiceCallback mBluetoothLeServiceCallback = new BluetoothLeServiceCallback() {
		
		@Override
		public void onTycheDisconnect() {
			if(DBG) Log.v(TAG, "#mBluetoothLeServiceCallback: onTycheDisconnect()");
			mMinBattVal = 255;
			for(Callback callback : mCallbacks) {
				callback.onTycheDisconnect();
			}
		}
		
		@Override
		public void onTycheConnect() {
			if(DBG) Log.v(TAG, "#mBluetoothLeServiceCallback: onTycheConnect()");
			for(Callback callback : mCallbacks) {
				callback.onTycheConnect();
			}
		}
		
		@Override
		public void onReceive(StatusData status) {
//			if(DBG) Log.v(TAG, "#mBluetoothLeServiceCallback: onReceive()");

			if(status.getLeftWheelVelocity()==0 && status.getRightWheelVelocity()==0) {
				mMinBattVal = Math.min(status.getBattery(), mMinBattVal);
			}
			status.setBattery(mMinBattVal);

			for(Callback callback : mCallbacks) {
				callback.onReceive(status);
			}
		}
	};
	
	private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            mBluetoothLeService.setCallback(mBluetoothLeServiceCallback);
            if(DBG) Log.d(TAG, "#ServiceConnection: onServiceConnected()");

            mBluetoothLeService.connect(mTycheAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        	if(DBG) Log.d(TAG, "#ServiceConnection: onServiceDisconnected()");
            mBluetoothLeService = null;
        }
    };
    
    private BroadcastReceiver mTycheDiscoveryReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(BluetoothLeScanActivity.ACTION_TYCHE_FOUND.equals(action)) {
				String address = intent.getStringExtra(BluetoothLeScanActivity.EXTRA_TYCHE_DEVICE_ADDRESS);
				String name = intent.getStringExtra(BluetoothLeScanActivity.EXTRA_TYCHE_DEVICE_NAME);
				if(DBG) Log.d(TAG, "#BroadcastReceiver: ACTION_TYCHE_FOUND $(" + address + ", " + name + ")");
				
				for(Callback callback : mCallbacks) {
					callback.onTycheFound(address, name);
				}
				
				mTycheAddress = address;

				bindService(context);
			}
			else if(BluetoothLeScanActivity.ACTION_TYCHE_NOT_FOUND.equals(action)) {
				if(DBG) Log.d(TAG, "#BroadcastReceiver: ACTION_TYCHE_NOT_FOUND");
				for(Callback callback : mCallbacks) {
					callback.onTycheNotFound();
				}
			}
			else if(BluetoothLeService.ACTION_TURNED_OFF_TYCHE.equals(action)) {
				if(DBG) Log.d(TAG, "#BroadcastReceiver: ACTION_TURNED_OFF_TYCHE");

				mTycheAddress = null;

				unbindService(context);
				//unregisterReceiver(context);
			}
			else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				if(DBG) Log.d(TAG, "#BroadcastReceiver: Bluetooth State Changed. state : " + state);

				switch (state) {
					case BluetoothAdapter.STATE_ON:
						if (!TextUtils.isEmpty(mTycheAddress)) {
							bindService(context);
						}
						break;
					case BluetoothAdapter.STATE_OFF:
						for(Callback callback : mCallbacks) {
							callback.onTycheDisconnect();
						}

						unbindService(context);
						break;
					case BluetoothAdapter.STATE_TURNING_ON:
						break;
					case BluetoothAdapter.STATE_TURNING_OFF:
						break;
				}
			}
		}
    	
    };
    
	private BluetoothLeManager() {
		
	}
	
	public static synchronized BluetoothLeManager getInstance() {
		if(sInstance == null) {
			sInstance = new BluetoothLeManager();
		}
		
		return sInstance;
	}
	
	public static void releaseInstance() {
		sInstance = null;
	}
	
	public void open(Context context) {
		open(context, null);
	}
	
	public void open(Context context, Callback callback) {
		addCallback(callback);
		
		Context applicationContext = context.getApplicationContext();
		registerReceiver(applicationContext);
		
		if(!isConnected()) {
			Intent intent = new Intent(applicationContext, BluetoothLeScanActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			applicationContext.startActivity(intent);
		}
	}
	
	public void close(Context context) {
		close(context, null);
	}
	
	public void close(Context context, Callback callback) {
		Context applicationContext = context.getApplicationContext();
		mTycheAddress = null;

		//requestDisconnect();
		
		unbindService(applicationContext);
		unregisterReceiver(applicationContext);
		
		removeCallback(callback);
	}
		
	public void addCallback(Callback callback) {
		if(callback == null) {
			return;
		}
		
		if(!mCallbacks.contains(callback)) {
			mCallbacks.add(callback);
		}
	}
	
	public void removeCallback(Callback callback) {
		if(callback == null) {
			return;
		}
		
		mCallbacks.remove(callback);
	}
	
	private void bindService(Context context) {
		if(mBluetoothLeService != null) {
			return;
		}
		Intent gattServiceIntent = new Intent(context, BluetoothLeService.class);
        context.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if(DBG) Log.d(TAG, "bind BLE service");
	}
	
	private void unbindService(Context context) {
		if(mBluetoothLeService == null) {
			return;
		}

		mBluetoothLeService.disconnect();

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		context.unbindService(mServiceConnection);
		mBluetoothLeService = null;
		if(DBG) Log.d(TAG, "unbind BLE service");
	}
	
	private void registerReceiver(Context context) {
		synchronized(mIsReceiverRegistered) {
			if(!mIsReceiverRegistered) {
				context.registerReceiver(mTycheDiscoveryReceiver, buildIntentFilter());
				mIsReceiverRegistered = true;
				if(DBG) Log.d(TAG, "TycheScanBroadcast Receiver is registered.");
			}
		}
	}
	
	private void unregisterReceiver(Context context) {
		synchronized(mIsReceiverRegistered) {
			if(mIsReceiverRegistered) {
				context.unregisterReceiver(mTycheDiscoveryReceiver);
				mIsReceiverRegistered = false;
				if(DBG) Log.d(TAG, "TycheScanBroadcast Receiver is unregistered.");
			}
		}
	}
	
	private IntentFilter buildIntentFilter() {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeScanActivity.ACTION_TYCHE_FOUND);
		intentFilter.addAction(BluetoothLeScanActivity.ACTION_TYCHE_NOT_FOUND);
		intentFilter.addAction(BluetoothLeService.ACTION_TURNED_OFF_TYCHE);
		intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		
		return intentFilter;
	}
	
	public boolean isConnected() {
		return mBluetoothLeService != null;
	}
	
	public boolean requestChangeWheelVelocityRpm(int leftVelocity, int rightVelocity) {
		return mBluetoothLeService != null 
				&& mBluetoothLeService.requestChangeWheelVelocityRpm(leftVelocity, rightVelocity);
	}
	
	public boolean requestChangeWheelVelocity(int leftVelocity, int rightVelocity) {
		return mBluetoothLeService != null 
				&& mBluetoothLeService.requestChangeWheelVelocity(leftVelocity, rightVelocity);
	}
	
	public boolean requestHeadlight(boolean isOn) {
		return mBluetoothLeService != null 
				&& mBluetoothLeService.requestHeadlight(isOn);
	}

	public boolean requestHeadligthBrightness(int brightness) {
		return mBluetoothLeService != null
				&& mBluetoothLeService.requestHeadligthBrightness(brightness);
	}
	
	public StatusData getCurrentStatusData() {
		return mBluetoothLeService != null? mBluetoothLeService.getCurrentStatusData() : null; 
	}
		
}
