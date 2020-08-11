package com.aibrain.tyche.bluetoothle.executor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;
import com.aibrain.tyche.bluetoothle.constants.Velocity;

public class SensorBaseRotateExecutor extends Executor implements SensorEventListener {

	private static final int SENSOR_INIT_DURATION = 500;
	private static final int TIME_OUT = 5000;

	private SensorManager mSensorManager;
	private Sensor mRotationVector;

	private float mInR[] = new float[9];
	private float mOutR[] = new float[9];
	private float mOrientation[] = new float[9];

	private float mConvertedValues;
	private int mAngle = 0;

	public SensorBaseRotateExecutor(Context context, int angle) {
		mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		mAngle = angle;
	}

	@Override
	protected void doJob() {
		mSensorManager.registerListener(this, mRotationVector, SensorManager.SENSOR_DELAY_FASTEST);
		
		for(int i=0; i<SENSOR_INIT_DURATION; i++) {
			if(isCanceled()) {
				mSensorManager.unregisterListener(this);
				return;
			}
			try {
				Thread.sleep(1);
			}catch(InterruptedException e) {}
		}
		
		int measuredAngle = (int)mConvertedValues + 180;
		int standard = (mAngle>0)? (measuredAngle+mAngle)%360 : (measuredAngle+mAngle+360)%360;
		int velocity = (mAngle>0)? Velocity.TURN : -Velocity.TURN;
		
		BluetoothLeManager.getInstance().requestChangeWheelVelocity(velocity, -velocity);
		
		long startTime =  SystemClock.elapsedRealtime(); 
		while(SystemClock.elapsedRealtime()-startTime < TIME_OUT) {
			int currentAngle = (int)mConvertedValues + 180;
			if(Math.abs(standard-currentAngle) <= 6) {
				break;
			}
			
			if(isCanceled()) break;
		}
		
		BluetoothLeManager.getInstance().requestChangeWheelVelocity(0, 0);
		
		mSensorManager.unregisterListener(this);
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		Sensor sensor = event.sensor;
		if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(mInR, event.values);
            SensorManager.remapCoordinateSystem(mInR,
                            SensorManager.AXIS_X, SensorManager.AXIS_Z,
                            mOutR);
            SensorManager.getOrientation(mOutR, mOrientation);

            // Optionally convert the result from radians to degrees
            mConvertedValues = (float) Math.toDegrees(mOrientation[0]);

            //System.out.println("angle: " + mConvertedValues);
        }
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		
	}

}
