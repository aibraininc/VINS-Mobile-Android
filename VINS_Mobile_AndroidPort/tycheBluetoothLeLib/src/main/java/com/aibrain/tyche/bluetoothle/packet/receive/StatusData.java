package com.aibrain.tyche.bluetoothle.packet.receive;

public class StatusData {

	private byte mCommand = 0;
	private int mDistance = 0;
	private boolean mHeadlight = false;
	private int mLeftWheelVelocity = 0;
	private int mRightWheelVelocity = 0;
	private int mLeftEncPosition = 0;
	private int mRightEncPosition = 0;
	private int mBattery = 0;
	private int mFirmwareVersionCode = 0;
	private int mSerialNumber = 0;

	public void setCommand(byte cmd) {
		mCommand = cmd;
	}

	public byte getCommand() {
		return mCommand;
	}

	public void setDistance(int distance) {
		mDistance = distance;
	}

	public int getDistance() {
		return mDistance;
	}

	public void setHeadlight(boolean isOn) {
		mHeadlight = isOn;
	}

	public boolean getHeadlight() {
		return mHeadlight;
	}

	public void setLeftWheelVelocity(int velocity) {
		mLeftWheelVelocity = velocity;
	}

	public int getLeftWheelVelocity() {
		return mLeftWheelVelocity;
	}

	public void setRightWheelVelocity(int velocity) {
		mRightWheelVelocity = velocity;
	}

	public int getRightWheelVelocity() {
		return mRightWheelVelocity;
	}

	// battery quantity is between 175 and 210
	public void setBattery(int quantity) {
		mBattery = quantity;
	}

	public int getBattery() {
		return mBattery;
	}

	public void setFirmwareVersionCode(int versionCode) {
		mFirmwareVersionCode = versionCode;
	}

	public int getFirmwareVersion() {
		return mFirmwareVersionCode;
	}

	public void setSerialNumber(int serialNum) {
		mSerialNumber = serialNum;
	}

	public int getSerialNumber() {
		return mSerialNumber;
	}

	public void setLeftEncPosition(int leftEncPos) {
		mLeftEncPosition = leftEncPos;
	}

	public int getLeftEncPosition() {
		return mLeftEncPosition;
	}

	public void setRightEncPosition(int rightEncPos) {
		mRightEncPosition = rightEncPos;
	}

	public int getRightEncPosition() {
		return mRightEncPosition;
	}

	@Override
	public String toString() {
		return "StatusData{" +
				"mCommand=" + mCommand +
				", mDistance=" + mDistance +
				", mHeadlight=" + mHeadlight +
				", mLeftWheelVelocity=" + mLeftWheelVelocity +
				", mRightWheelVelocity=" + mRightWheelVelocity +
				", mLeftEncPosition=" + mLeftEncPosition +
				", mRightEncPosition=" + mRightEncPosition +
				", mBattery=" + mBattery +
				", mFirmwareVersionCode=" + mFirmwareVersionCode +
				", mSerialNumber=" + mSerialNumber +
				'}';
	}
}