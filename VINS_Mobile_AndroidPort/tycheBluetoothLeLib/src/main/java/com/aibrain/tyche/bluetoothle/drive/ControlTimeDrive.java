package com.aibrain.tyche.bluetoothle.drive;

import com.aibrain.tyche.bluetoothle.constants.Mode;

public class ControlTimeDrive extends Drive {

    private int mDuration = 0;
    private int mLeftVelocity = 0;
    private int mRightVelocity = 0;

    public ControlTimeDrive() {
        mMode = Mode.TIME;
    }

    public void setDuration(int durationMs) {
        mDuration = durationMs;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setLeftVelocity(int leftVelocity) {
        mLeftVelocity = leftVelocity;
    }

    public void setRightVelocity(int rightVelocity) {
        mRightVelocity = rightVelocity;
    }

    public int getLeftVelocity() {
        return mLeftVelocity;
    }
    public int getRightVelocity() { return mRightVelocity; }

}
