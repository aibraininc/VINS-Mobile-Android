package com.aibrain.tyche.bluetoothle;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import com.aibrain.tyche.bluetoothle.constants.Direction;
import com.aibrain.tyche.bluetoothle.constants.Mode;
import com.aibrain.tyche.bluetoothle.constants.Velocity;
import com.aibrain.tyche.bluetoothle.drive.ControlTimeDrive;
import com.aibrain.tyche.bluetoothle.drive.Drive;
import com.aibrain.tyche.bluetoothle.drive.MoveDrive;
import com.aibrain.tyche.bluetoothle.drive.RotateDrive;
import com.aibrain.tyche.bluetoothle.drive.TimeDrive;
import com.aibrain.tyche.bluetoothle.exception.InvalidNumberException;
import com.aibrain.tyche.bluetoothle.exception.NotConnectedException;
import com.aibrain.tyche.bluetoothle.exception.NotEnoughBatteryException;
import com.aibrain.tyche.bluetoothle.exception.NotSupportSensorException;
import com.aibrain.tyche.bluetoothle.executor.ContinuousMoveExecutor;
import com.aibrain.tyche.bluetoothle.executor.ContinuousObstacleDetectingMoveExecutor;
import com.aibrain.tyche.bluetoothle.executor.EncoderBaseMoveExecutor;
import com.aibrain.tyche.bluetoothle.executor.EncoderBaseObstacleDetectingMoveExecutor;
import com.aibrain.tyche.bluetoothle.executor.EncoderBaseRotateExecutor;
import com.aibrain.tyche.bluetoothle.executor.Executor;
import com.aibrain.tyche.bluetoothle.executor.Executor.OnFinishListener;
import com.aibrain.tyche.bluetoothle.executor.ExecutorManager;
import com.aibrain.tyche.bluetoothle.executor.OnObstacleDetectedListener;
import com.aibrain.tyche.bluetoothle.executor.SensorBaseRotateExecutor;
import com.aibrain.tyche.bluetoothle.executor.TimeBaseMoveExecutor;
import com.aibrain.tyche.bluetoothle.executor.TimeBaseObstacleDetectingMoveExecutor;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

import java.util.ArrayList;

public class TycheControlHelper extends BluetoothLeOpenHelper {

	public interface OnChangeStatusListener {
		void onConnectionStatusChange(boolean isConnect);
		void onStatusChange(StatusData status);
		void onObstacleDetected(int distance);
		void onNotEnoughBattery();
	}

	private Context mContext;

	private BluetoothLeManager mBluetoothLeManager;
	private OnChangeStatusListener mOnChangeStatusListener;
	private ExecutorManager mExecutorManager;

	private ArrayList<Drive> mObstacleDetectedDrives = new ArrayList<Drive>();
	private OnFinishListener mObstacleDetectedDrivesFinishListener;
	private TimeDrive mDefaultObstacleDetectedDrive;
	private boolean mIsEnableObstacleDetector = false;

	private long mNotifyBattSendTime = 0;
	private StatusData mTycheStatus;
	private static final int BATT_NOTIFY_PERIOD = 10000;
	private static final int BATT_THRESHOLD = 175;

	public TycheControlHelper(Context context) {
		this(context, null);
	}

	public TycheControlHelper(Context context, OnChangeStatusListener listener) {
		super(context);

		mContext = context;
		mOnChangeStatusListener = listener;

		mExecutorManager = new ExecutorManager();
		mExecutorManager.start();

		mDefaultObstacleDetectedDrive = new TimeDrive();
		mDefaultObstacleDetectedDrive.setDirection(Direction.BACKWARD);
		mDefaultObstacleDetectedDrive.setDuration(300);
		mDefaultObstacleDetectedDrive.setVelocity(Velocity.VERY_SLOW);
		mObstacleDetectedDrives.add(mDefaultObstacleDetectedDrive);
	}

	@Override
	public void onCreate(BluetoothLeManager bleManager) {
		bleManager.open(mContext);
	}

	@Override
	public void onConnectionStateChange(boolean isConnect) {
		if(mOnChangeStatusListener != null) {
			mOnChangeStatusListener.onConnectionStatusChange(isConnect);
		}
	}

	@Override
	public void onReceive(StatusData status) {
		mTycheStatus = status;

		if(mOnChangeStatusListener != null) {
			mOnChangeStatusListener.onStatusChange(status);
		}

		if(status.getLeftWheelVelocity()>0 && status.getRightWheelVelocity()>0) {
			processObstacleDetected(status);
		}

		if(status.getBattery() < BATT_THRESHOLD) {
			if(SystemClock.elapsedRealtime()-mNotifyBattSendTime > BATT_NOTIFY_PERIOD) {
				if(mOnChangeStatusListener != null) {
					mOnChangeStatusListener.onNotEnoughBattery();
				}
				mNotifyBattSendTime = SystemClock.elapsedRealtime();
			}
		}
	}

	public void open() {
		synchronized(this) {
			if(mExecutorManager == null) {
				mExecutorManager = new ExecutorManager();
				mExecutorManager.start();
			}
		}

		mBluetoothLeManager = super.getBleManager();
	}

	@Override
	public void close(boolean keepConnected) {
		synchronized(this) {
			if(mExecutorManager != null) {
				mExecutorManager.cancelAll();
				mExecutorManager.setRunning(false);
				int tryCount = 5;
				while(--tryCount>0) {
					try {
						mExecutorManager.join();
						break;
					} catch(InterruptedException e) {
						e.printStackTrace();
					}
				}
				mExecutorManager = null;
			}
		}

		mBluetoothLeManager = null;
		super.close(keepConnected);
	}

	public boolean isOpened() {
		return mBluetoothLeManager != null;
	}

	// only return true if TycheControlHelper.open() is called and phone is connected to Tyche.
	public boolean isConnected() {
		return isOpened() && mBluetoothLeManager.isConnected();
	}

	public static boolean isTycheConnected() {
		return BluetoothLeManager.getInstance().isConnected();
	}

	public void drive(Drive drive, OnFinishListener listener)
			throws NotConnectedException, NotEnoughBatteryException,
				   NotSupportSensorException, InvalidNumberException {
		if(drive == null) 								return;
		if(!isOpened()) 								throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected())			throw new NotConnectedException();
		if(mTycheStatus != null && mTycheStatus.getBattery()<BATT_THRESHOLD) 	throw new NotEnoughBatteryException();

		Executor executor = makeExecutorByDrive(drive);
		if(executor != null) {
			executor.setOnFinishListener(listener);
			mExecutorManager.add(executor);
		}
	}

	public void drive(Drive drive)
			throws NotConnectedException, NotEnoughBatteryException,
				   InvalidNumberException, NotSupportSensorException {
		drive(drive, null);
	}

	public void drive(ArrayList<Drive> drives, OnFinishListener listener)
			throws NotConnectedException, NotEnoughBatteryException,
				   InvalidNumberException, NotSupportSensorException {
		if(drives == null) 								return;
		if(!isOpened()) 								throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected()) 			throw new NotConnectedException();
		if(mTycheStatus != null && mTycheStatus.getBattery()<BATT_THRESHOLD) 	throw new NotEnoughBatteryException();

		for(int i=0; i<drives.size(); i++) {
			Drive drive = drives.get(i);
			Executor executor = makeExecutorByDrive(drive);
			if(executor != null) {
				if(i == drives.size()-1) {
					executor.setOnFinishListener(listener);
				}
				mExecutorManager.add(executor);
			}
		}
	}

	public void drive(ArrayList<Drive> drives)
			throws NotConnectedException, NotEnoughBatteryException,
				   InvalidNumberException, NotSupportSensorException {
		drive(drives, null);
	}

	public void skipCurrentDrive() {
		mExecutorManager.cancelCurrent();
	}

	public void skipAllDrives() {
		mExecutorManager.cancelAll();
	}

	public void operate(int leftVelocity, int rightVelocity)
			throws NotConnectedException, NotEnoughBatteryException {
		if(!isOpened()) 								throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected()) 			throw new NotConnectedException();

		skipAllDrives();

		if(leftVelocity == 0 && rightVelocity == 0) {
			mBluetoothLeManager.requestChangeWheelVelocity(0, 0);
			return;
		}
		else {
			if(mTycheStatus != null && mTycheStatus.getBattery()<BATT_THRESHOLD) throw new NotEnoughBatteryException();
		}

		Executor executor = mIsEnableObstacleDetector
				? new ContinuousObstacleDetectingMoveExecutor(leftVelocity, rightVelocity, mOnObstacleDetectedListener)
				: new ContinuousMoveExecutor(leftVelocity, rightVelocity);
		mExecutorManager.add(executor);
	}

	public void turnHeadlight(boolean isOn) throws NotConnectedException {
		if(!isOpened()) 						throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected()) 	throw new NotConnectedException();

		mBluetoothLeManager.requestHeadlight(isOn);
	}

	public void setHeadlightBrightness(int brightness) throws NotConnectedException {
		if(!isOpened()) 						throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected()) 	throw new NotConnectedException();

		mBluetoothLeManager.requestHeadligthBrightness(brightness);
	}

	public void enableObstacleDetector(boolean isEnable) {
		mIsEnableObstacleDetector = isEnable;
	}

	public void setObstacleDetectedDrive(Drive drive) {
		setObstacleDetectedDrive(drive, null);
	}

	public void setObstacleDetectedDrive(Drive drive, OnFinishListener listener) {
		mObstacleDetectedDrives.clear();
		if(drive != null) {
			mObstacleDetectedDrives.add(drive);
		}
		else {
			mObstacleDetectedDrives.add(mDefaultObstacleDetectedDrive);
		}
		mObstacleDetectedDrivesFinishListener = listener;
	}

	public void setObstacleDetectedDrives(ArrayList<Drive> drives) {
		setObstacleDetectedDrives(drives, null);
	}

	public void setObstacleDetectedDrives(ArrayList<Drive> drives, OnFinishListener listener) {
		if(drives != null) {
			mObstacleDetectedDrives = drives;
		}
		else {
			mObstacleDetectedDrives.clear();
			mObstacleDetectedDrives.add(mDefaultObstacleDetectedDrive);
		}
		mObstacleDetectedDrivesFinishListener = listener;
	}

	public boolean isBatteryEnough() throws NotConnectedException {
		if(!isOpened()) 												throw new NotConnectedException("First, you have to call open() method before using this library.");
		if(!mBluetoothLeManager.isConnected() || mTycheStatus == null) 	throw new NotConnectedException();

		return mTycheStatus.getBattery()>=BATT_THRESHOLD;
	}

	private void processObstacleDetected(StatusData status) {
		if(status.getDistance()>0 &&
				status.getDistance() < Math.max(Math.abs(status.getLeftWheelVelocity()), Math.abs(status.getRightWheelVelocity()))/3) {
			if(mOnChangeStatusListener != null) {
				mOnChangeStatusListener.onObstacleDetected(status.getDistance());
			}
		}
	}

	private boolean hasSensor() {
		PackageManager manager = mContext.getPackageManager();
		return manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE);
	}

	private Executor makeExecutorByControlTimeDrive(ControlTimeDrive drive) throws InvalidNumberException {
		if(drive.getDuration() <= 0) 		throw new InvalidNumberException("Duration must be positive integer.");

		int leftVel = drive.getLeftVelocity();
		int rightVel = drive.getRightVelocity();

		return mIsEnableObstacleDetector
				?	new TimeBaseObstacleDetectingMoveExecutor(drive.getDuration(), leftVel, rightVel, mOnObstacleDetectedListener)
				:	new TimeBaseMoveExecutor(drive.getDuration(), leftVel, rightVel);
	}

	private Executor makeExecutorByTimeDrive(TimeDrive drive) throws InvalidNumberException {
		if(drive.getDuration() <= 0) 		throw new InvalidNumberException("Duration must be positive integer.");

		int velocity = Math.abs(drive.getVelocity());
		int leftVel;
		int rightVel;

		Direction dir = drive.getDirection();
		if(dir.equals(Direction.FORWARD)) {
			leftVel = velocity;
			rightVel = velocity;
		}
		else if(dir.equals(Direction.BACKWARD)) {
			leftVel = -velocity;
			rightVel = -velocity;
		}
		else if(dir.equals(Direction.LEFT)) {
			leftVel = -velocity;
			rightVel = velocity;
		}
		else if(dir.equals(Direction.RIGHT)) {
			leftVel = velocity;
			rightVel = -velocity;
		}
		else {
			return null;
		}

		return mIsEnableObstacleDetector
				?	new TimeBaseObstacleDetectingMoveExecutor(drive.getDuration(), leftVel, rightVel, mOnObstacleDetectedListener)
				:	new TimeBaseMoveExecutor(drive.getDuration(), leftVel, rightVel);
	}

	private Executor makeExecutorByRotateDrive(RotateDrive drive) throws InvalidNumberException, NotSupportSensorException {
		if(drive.getAngle()>360 || drive.getAngle()<-360) 	throw new InvalidNumberException("Angle must be smaller than 360 degree and larger than -360 degree.");
		if(drive.getMode() == Mode.SENSOR && !hasSensor()) 	throw new NotSupportSensorException();

		return 	(drive.getMode() == Mode.ENCODER) ? new EncoderBaseRotateExecutor(drive.getAngle()) :
				(drive.getMode() == Mode.SENSOR)  ? new SensorBaseRotateExecutor(mContext, drive.getAngle()) :
						null;
	}

	private Executor makeExecutorByMoveDrive(MoveDrive drive) throws InvalidNumberException {
		if(drive.getVelocity() <= 0) throw new InvalidNumberException("Velocity must be positive integer.");

		return mIsEnableObstacleDetector
				? new EncoderBaseObstacleDetectingMoveExecutor(drive.getDistance(), drive.getVelocity(), mOnObstacleDetectedListener)
				: new EncoderBaseMoveExecutor(drive.getDistance(), drive.getVelocity());
	}

	private Executor makeExecutorByDrive(Drive drive) throws InvalidNumberException, NotSupportSensorException {
		if(drive.getRestTime() <= 0) throw new InvalidNumberException("Rest time must be positive integer.");

		Executor executor = null;
		if(drive instanceof ControlTimeDrive) {
			executor = makeExecutorByControlTimeDrive((ControlTimeDrive)drive);
		}
		if (drive instanceof TimeDrive) {
			executor = makeExecutorByTimeDrive((TimeDrive)drive);
		}
		else if(drive instanceof RotateDrive) {
			executor = makeExecutorByRotateDrive((RotateDrive)drive);
		}
		else if(drive instanceof MoveDrive) {
			executor = makeExecutorByMoveDrive((MoveDrive)drive);
		}

		if(executor != null) {
			executor.setRestTime(drive.getRestTime());
		}

		return executor;
	}

	private OnObstacleDetectedListener mOnObstacleDetectedListener = new OnObstacleDetectedListener() {

		@Override
		public void onDetected() {
			for(int i=mObstacleDetectedDrives.size()-1; i>=0; i--) {
				Drive drive = mObstacleDetectedDrives.get(i);
				try {
					Executor executor = makeExecutorByDrive(drive);
					if(i == mObstacleDetectedDrives.size()-1) {
						executor.setOnFinishListener(mObstacleDetectedDrivesFinishListener);
					}
					mExecutorManager.addFirst(executor);
				}
				catch(InvalidNumberException|NotSupportSensorException e) {
					e.printStackTrace();
				}
			}
		}

	};

}
