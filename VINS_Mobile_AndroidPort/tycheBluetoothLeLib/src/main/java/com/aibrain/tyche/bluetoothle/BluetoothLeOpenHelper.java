package com.aibrain.tyche.bluetoothle;

import android.content.Context;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager.Callback;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

public abstract class BluetoothLeOpenHelper {
	
	private static final String TAG = BluetoothLeOpenHelper.class.getSimpleName();
	
	private final Context mContext;
	
	private String mTycheAddress;
	private String mTycheName;
	
	private BluetoothLeManager mBluetoothLeManager;
	private Callback mBluetoothLeCallback = new Callback() {
		
		@Override
		public void onTycheNotFound() {
			onConnectionStateChange(false);
		}
		
		@Override
		public void onTycheFound(String address, String name) {
			mTycheAddress = address;
			mTycheName = name;
		}
		
		@Override
		public void onTycheDisconnect() {
			mTycheAddress = "";
			mTycheName = "";
			
			onConnectionStateChange(false);
		}
		
		@Override
		public void onTycheConnect() {
			onConnectionStateChange(true);
		}
		
		@Override
		public void onReceive(StatusData status) {
			BluetoothLeOpenHelper.this.onReceive(status);
		}
	}; 
	
	public BluetoothLeOpenHelper(Context context) {
		mContext = context;
	}
	
	public BluetoothLeManager getBleManager() {
		synchronized(this) {
			if(mBluetoothLeManager != null) {
				if(!mBluetoothLeManager.isConnected()) {
					mBluetoothLeManager = null;
					mTycheAddress = "";
					mTycheName = "";
				}
				else {
					return mBluetoothLeManager;
				}
			}
		
			mBluetoothLeManager = BluetoothLeManager.getInstance();
			if(!mBluetoothLeManager.isConnected()) {
				onCreate(mBluetoothLeManager);
			}
			mBluetoothLeManager.addCallback(mBluetoothLeCallback);
			
			return mBluetoothLeManager;
		}
	}
	
	public void close(boolean keepConnected) {
		synchronized(this) {
			if(mBluetoothLeManager != null) {
				if(!keepConnected) {
					mBluetoothLeManager.close(mContext);
				}
				mBluetoothLeManager.removeCallback(mBluetoothLeCallback);
				mBluetoothLeManager = null;
			}
		}
	}
	
	public void releaseBleManager() {
		BluetoothLeManager.releaseInstance();
	}
	
	public String getTycheAddress() {
		return mTycheAddress;
	}
	
	public String getTycheName() {
		return mTycheName;
	}
	
	public abstract void onCreate(BluetoothLeManager bleManager);
	public abstract void onConnectionStateChange(boolean isConnect);
	public abstract void onReceive(StatusData status);
	
}
