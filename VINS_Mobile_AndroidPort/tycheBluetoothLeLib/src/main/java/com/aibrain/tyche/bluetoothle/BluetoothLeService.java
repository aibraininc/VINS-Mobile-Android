package com.aibrain.tyche.bluetoothle;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.packet.Connect;
import com.aibrain.tyche.bluetoothle.packet.Disconnect;
import com.aibrain.tyche.bluetoothle.packet.Headlight;
import com.aibrain.tyche.bluetoothle.packet.HeadlightBrightness;
import com.aibrain.tyche.bluetoothle.packet.MotorPwm;
import com.aibrain.tyche.bluetoothle.packet.MotorRpm;
import com.aibrain.tyche.bluetoothle.packet.Status;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusPacketBuilder;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class BluetoothLeService extends Service {

	private static final String TAG = BluetoothLeService.class.getSimpleName();
	private static final boolean DBG = false;
	
	private static final int RECEIVE_PACKET_SIZE = 20;
	
	public static final String ACTION_TURNED_OFF_TYCHE = 
			"com.aibrain.tyche.bluetoothle.ACTION_TURNED_OFF_TYCHE";
	public static final String ACTION_TYCHE_CONNECTED =
			"com.aibrain.tyche.bluetoothle.ACTION_TYCHE_CONNECTED";
	public static final String ACTION_TYCHE_DISCONNECTED =
			"com.aibrain.tyche.bluetoothle.ACTION_TYCHE_DISCONNECTED";
	
	private static String TYCHE_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
	
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mBluetoothGatt;
	private BluetoothGattCharacteristic mDefaultChar;
	
	private CharacteristicWriter mCharacteristicWriter;
	
	private StatusData mTycheStatusData;
	
	private boolean mIsInitializing = false;

	public interface BluetoothLeServiceCallback {
		void onTycheConnect();
		void onTycheDisconnect();
		void onReceive(StatusData status);
	}
    private BluetoothLeServiceCallback mBluetoothLeServiceCallback;
    
    public void setCallback(BluetoothLeServiceCallback callback) {
    	mBluetoothLeServiceCallback = callback;
    }
    
	public class LocalBinder extends Binder {
		
		public BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
		
	}
	
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			
			if(DBG) Log.d(TAG, "#BluetoothGattCallback: onConnectionStateChange(): " + newState);
			
			if(newState == BluetoothProfile.STATE_CONNECTED) {
				boolean result = mBluetoothGatt.discoverServices();
				if(DBG) Log.d(TAG, "connected to GATT server.");
				if(DBG) Log.d(TAG, "attempt to start service discovery: " + result);
			}
			else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
				if(DBG) Log.d(TAG, "disconnected from GATT server");
				
				//disconnect();
				//close();
				
				if(mBluetoothLeServiceCallback != null) {
					mBluetoothLeServiceCallback.onTycheDisconnect();
				}
				
				Intent intent1 = new Intent(ACTION_TURNED_OFF_TYCHE);
				sendBroadcast(intent1);

				Intent intent2 = new Intent(ACTION_TYCHE_DISCONNECTED);
				sendBroadcast(intent2);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if(DBG) Log.d(TAG, "#BluetoothGattCallback: onServicesDiscovered() received: " + status);
			if(status == BluetoothGatt.GATT_SUCCESS) {
				if(checkGattServices(gatt.getServices())) {
					// wait for bluetooth setting
					try {
						Thread.sleep(500);
					} catch(InterruptedException e) {
						e.printStackTrace();
					}

					new Thread() {
						public void run() {
							requestConnect();
							mCharacteristicWriter.enableStatusData(true);

							mIsInitializing = false;

							if(mBluetoothLeServiceCallback != null) {
								mBluetoothLeServiceCallback.onTycheConnect();
							}

							Intent intent = new Intent(ACTION_TYCHE_CONNECTED);
							sendBroadcast(intent);
						}
					}.start();
				}
			}
			else {
				if(mBluetoothLeServiceCallback != null) {
					mBluetoothLeServiceCallback.onTycheDisconnect();
				}

				Intent intent1 = new Intent(ACTION_TURNED_OFF_TYCHE);
				sendBroadcast(intent1);

				Intent intent2 = new Intent(ACTION_TYCHE_DISCONNECTED);
				sendBroadcast(intent2);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			if(DBG) Log.d(TAG, "#BluetoothGattCallback: onCharacteristicRead(): " + characteristic.getUuid().toString());
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			//if(DBG) Log.d(TAG, "#BluetoothGattCallback: onCharacteristicChanged(): " + characteristic.getUuid().toString());
			final byte[] recvData = characteristic.getValue();
			if(recvData != null) {
				//Log.d(TAG, "recv data size: " + recvData.length);
				if(recvData.length == RECEIVE_PACKET_SIZE) {
					StatusData status = StatusPacketBuilder.createStatus(recvData);
					if(status != null) {
						mTycheStatusData = status;
						if(mBluetoothLeServiceCallback != null) {
							mBluetoothLeServiceCallback.onReceive(status);
						}
					}
				}
				else {
					byte[] packet = StatusPacketBuilder.createStatusPacket(recvData);
					if(packet != null) {
						Log.d(TAG, "made Status packet");
						StatusData status = StatusPacketBuilder.createStatus(packet);
						if(status !=null) {
							mTycheStatusData = status;
						}
					}
				}
			}
		}
		
	};
	
	private final IBinder mBinder = new LocalBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	@Override
	public void onCreate() {
		if(mCharacteristicWriter == null) {
			mCharacteristicWriter = new CharacteristicWriter();
			mCharacteristicWriter.start();
			if(DBG) Log.d(TAG, "characteristic thread start!");
		}
		open();
		if(DBG) Log.d(TAG, "service onCreate()");
	}

	@Override
	public void onDestroy() {
		close();
		if(DBG) Log.d(TAG, "service onDestroy()");
		
		if(mCharacteristicWriter != null) {
			mCharacteristicWriter.end();
			int tryCount = 5;
			while(--tryCount>0) {
				try {
					mCharacteristicWriter.join();
					break;
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			mCharacteristicWriter = null;
			if(DBG) Log.d(TAG, "characteristic thread end!");
		}
	}

	private boolean open() {
		if(mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
			if(mBluetoothManager == null) {
				if(DBG) Log.d(TAG, "failed to get the system service.");
				return false;
			}
		}
		
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		return true;
	}
	
	private void close() {
		if(mBluetoothGatt == null) {
			return;
		}
		
		mBluetoothGatt.close();
		mBluetoothGatt = null;
	}
	
	public boolean connect(String address) {
		if(mBluetoothAdapter == null || address == null) {
			if(DBG) Log.d(TAG, "BluetoothAdapter is not initialized or address is not unspecified.");
			return false;
		}
		
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if(device == null) {
			if(DBG) Log.d(TAG, "Device is not found.");
			return false;
		}
		
		mIsInitializing = true;
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		if(DBG) Log.d(TAG, "create a new connection.");
		
		return true;
	}
	
	public void disconnect() {
		// prevent to disconnect while initializing
		long time = SystemClock.elapsedRealtime();
		while(mIsInitializing) {
			if(SystemClock.elapsedRealtime()-time > 1000) break;
		}
		
		mCharacteristicWriter.enableStatusData(false);
		requestDisconnect();
		
		// wait for writing remain data
		while(mCharacteristicWriter.hasValues()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {}
		}
		
		if(mBluetoothAdapter == null || mBluetoothGatt == null) {
			if(DBG) Log.d(TAG, "BluetoothAdapter is not initialized");
			return;
		}
		if(DBG) Log.d(TAG, "disconnect an established connection");
		mBluetoothGatt.disconnect();
	}
	
	private boolean checkGattServices(List<BluetoothGattService> gattServices) {
		if(gattServices == null) {
			if(DBG) Log.d(TAG, "GATT has no services");
			return false;
		}
		
		for(int i=0; i<gattServices.size(); i++) {
			BluetoothGattService gattService = gattServices.get(i);
			if(DBG) Log.d(TAG, "GATT Service #" + i + ": " + gattService.getUuid().toString());
			
			List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
			for(int j=0; j<gattCharacteristics.size(); j++) {
				BluetoothGattCharacteristic gattCharacteristic = gattCharacteristics.get(j);
				if(DBG) Log.d(TAG, "GATT Characteristic #" + j + ": " + gattCharacteristic.getUuid().toString());
				if(gattCharacteristic.getUuid().toString().equals(TYCHE_CHARACTERISTIC_UUID)) {
					if(isWritableCharacteristic(gattCharacteristic) 
							&& isNotificationCharacteristic(gattCharacteristic)) {
						setCharacteristicNotification(gattCharacteristic, true);
						mDefaultChar = gattCharacteristic;
						if(DBG) Log.d(TAG, "selected characteristic: (" + i + ", " + j + ")");
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	private boolean isWritableCharacteristic(BluetoothGattCharacteristic characteristic) {
		if(characteristic == null) {
			return false;
		}
		
		int properties = characteristic.getProperties();
		if(((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) |
				(properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
			if(DBG) Log.d(TAG, "w: O");
			return true;
		}
		else {
			if(DBG) Log.d(TAG, "w: X");
			return false;
		}
	}
	
	private boolean isReadableCharacteristic(BluetoothGattCharacteristic characteristic) {
		if(characteristic == null) return false;
		
		final int properties = characteristic.getProperties();
		if((properties & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
			if(DBG) Log.d(TAG, "r: O");
			return true;
		} else {
			if(DBG) Log.d(TAG, "r: X");
			return false;
		}
	}
	
	private boolean isNotificationCharacteristic(BluetoothGattCharacteristic characteristic) {
		if(characteristic == null) return false;
		
		final int properties = characteristic.getProperties();
		if((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
			if(DBG) Log.d(TAG, "n: O");
			return true;
		} else {
			if(DBG) Log.d(TAG, "n: X");
			return false;
		}
    }
	
	private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
        	if(DBG) Log.d(TAG, "BluetoothAdapter is not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }
    
    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
        	if(DBG) Log.d(TAG, "BluetoothAdapter is not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }
    
    public boolean write(byte[] data) {
    	if (mBluetoothAdapter == null || mBluetoothGatt == null) {
    		if(DBG) Log.d(TAG, "BluetoothGatt is not initialized");
		    return false;
		}
    
    	if(mDefaultChar == null) {
    		if(DBG) Log.d(TAG, "The characteristic of Tyche was not set.");
    		return false;
    	}

    	mCharacteristicWriter.addValue(data);
		return true;
	}
     
    public boolean requestChangeWheelVelocityRpm(int leftVelocity, int rightVelocity) {
    	if(leftVelocity == 0 && rightVelocity == 0) {
			mCharacteristicWriter.setStatusRequestPeriod(CharacteristicWriter.STATUS_REQUEST_PERIOD_STOPPING);
		}
		else {
			mCharacteristicWriter.setStatusRequestPeriod(CharacteristicWriter.STATUS_REQUEST_PERIOD_OPERATING);
		}
    	
		MotorRpm packet = new MotorRpm();
		return write(packet.getCommand(leftVelocity, rightVelocity));
	}
	
	public boolean requestChangeWheelVelocity(int leftVelocity, int rightVelocity) {
		if(leftVelocity == 0 && rightVelocity == 0) {
			mCharacteristicWriter.setStatusRequestPeriod(CharacteristicWriter.STATUS_REQUEST_PERIOD_STOPPING);
		}
		else {
			mCharacteristicWriter.setStatusRequestPeriod(CharacteristicWriter.STATUS_REQUEST_PERIOD_OPERATING);
		}

		MotorPwm packet = new MotorPwm();
		return write(packet.getCommand(leftVelocity, rightVelocity));
	}
	
	public boolean requestHeadlight(boolean isOn) {
		Headlight packet = new Headlight();
		return write(packet.getCommand(isOn));
	}

	public boolean requestHeadligthBrightness(int brightness) {
		HeadlightBrightness packet = new HeadlightBrightness();
		return write(packet.getCommand(brightness));
	}

    private boolean requestConnect() {
    	Connect packet = new Connect();
    	return write(packet.getCommand());
    }
    
    private boolean requestDisconnect() {
    	Disconnect packet = new Disconnect();
    	return write(packet.getCommand());
    }
    
    public StatusData getCurrentStatusData() {
    	return mTycheStatusData;
    }
	
    private class CharacteristicWriter extends Thread {
    	
    	private static final int STATUS_REQUEST_PERIOD_STOPPING = 500;
    	private static final int STATUS_REQUEST_PERIOD_OPERATING = 60;
    	
    	private final LinkedList<byte[]> characteristicValueList = new LinkedList<>();
    	private boolean isRunning = false;
    	private byte[] prevValue = new byte[1];
    	private long sendTime = 0;
    	
    	private boolean isEnabledStatus = false;
    	private long statusRequestTime = 0;
    	private int statusRequestPeriod = STATUS_REQUEST_PERIOD_STOPPING;

		private byte[] stopPacket = new MotorPwm().getCommand(0, 0);

    	@Override
    	public void run() {
    		isRunning = true;
    		while(isRunning) {
    			if(isEnabledStatus) {
    				if(SystemClock.elapsedRealtime()-statusRequestTime > statusRequestPeriod) {
    					addValue(new Status().getCommand());
    					statusRequestTime = SystemClock.elapsedRealtime();
    				}
    			}
    			
    			byte[] value;
    			synchronized(characteristicValueList) {
    				value = characteristicValueList.poll();
    			}
    			if(value != null) {
					if(value[2] != Command.STATUS) {
						if(Arrays.equals(value, prevValue) && SystemClock.elapsedRealtime()-sendTime < 1000) {
							if(DBG) Log.d(TAG, "same data");
							continue;
						}
						else {
							sendTime = SystemClock.elapsedRealtime();
							prevValue = value;
						}
					}

    				if(mDefaultChar != null) {
						mDefaultChar.setValue(value);
						mDefaultChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
						if(mBluetoothGatt != null) {
							mBluetoothGatt.writeCharacteristic(mDefaultChar);
							//if(DBG) Log.d(TAG, "#send data: " + value[2]);
							try {
								Thread.sleep(20);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}

					// stop packet is sent twice.
					if(Arrays.equals(value, stopPacket)) {
						if(mDefaultChar != null) {
							mDefaultChar.setValue(value);
							mDefaultChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
							if(mBluetoothGatt != null) {
								mBluetoothGatt.writeCharacteristic(mDefaultChar);
								if(DBG) Log.d(TAG, "#additional stop: " + value[2]);
								try {
									Thread.sleep(20);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
					}
    			}
    			
    			try {
    				Thread.sleep(1);
    			}catch(InterruptedException e) {}
    		}
    	}
    	
    	public void end() {
    		isRunning = false;
    	}
    	
    	public void addValue(byte[] value) {
    		synchronized(characteristicValueList) {
    			characteristicValueList.add(value);
    		}
    	}
    	
    	public boolean hasValues() {
    		synchronized(characteristicValueList) {
    			return !characteristicValueList.isEmpty();
    		}
    	}
    	
    	private void enableStatusData(boolean isEnable) {
    		isEnabledStatus = isEnable;
    	}
    	
    	private void setStatusRequestPeriod(int period) {
    		statusRequestPeriod = period;
    	}
    	
    }
    
}
