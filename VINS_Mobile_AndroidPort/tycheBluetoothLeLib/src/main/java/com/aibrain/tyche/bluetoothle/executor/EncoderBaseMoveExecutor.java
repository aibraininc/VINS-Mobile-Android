package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;
import com.aibrain.tyche.bluetoothle.constants.Velocity;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

/**
 * Created by SichangYang on 2017. 3. 20..
 */

public class EncoderBaseMoveExecutor extends EncoderBaseExecutor {

    private int mLeftVelocity = 0;
    private int mRightVelocity = 0;
    private int mDistanceCm = 0;

    private double mRatioThreshold = 0.0;
    private double mPrevRatio = 1.0;
    private double mBreakThreshold = 0.0;
    private int mCurrentPosition = 0;

    public EncoderBaseMoveExecutor(int distanceCm, int velocity) {
        if(distanceCm>0) {
            mLeftVelocity = velocity;
            mRightVelocity = velocity;
        }
        else {
            mLeftVelocity = -velocity;
            mRightVelocity = -velocity;
        }
        mDistanceCm = Math.abs(distanceCm);

        if(velocity >= Velocity.VERY_FAST) {
            mRatioThreshold = 1.0;
            mBreakThreshold = 0.35;
        }
        else if(velocity >= Velocity.FAST) {
            mRatioThreshold = 1.0;
            mBreakThreshold = 0.35;
        }
        else if(velocity >= Velocity.NORMAL) {
            mRatioThreshold = 1.0;
            mBreakThreshold = 0.35;
        }
        else if(velocity >= Velocity.SLOW) {
            mRatioThreshold = 2.0;
            mBreakThreshold = 0.55;
        }
        else if(velocity >= Velocity.VERY_SLOW) {
            mRatioThreshold = 2.0;
            mBreakThreshold = 0.55;
        }
        else {
            mRatioThreshold = 2.0;
            mBreakThreshold = 0.55;
        }
    }

    @Override
    protected boolean check() {
        StatusData status = BluetoothLeManager.getInstance().getCurrentStatusData();
        if(status == null) return false;

        // only left-wheel considered (certainly right-wheel is same to it)
        if(status.getLeftWheelVelocity() != 0) {
            int pos = status.getLeftEncPosition();

            double progress = (double) (mCurrentPosition + pos) / mDistanceCm;
            // calculate it to one decimal place.
            double ratio = Math.round((1 - progress) * 10) / 10.0 * mRatioThreshold;
            if(ratio > 1.0) ratio = 1.0;
            if (mPrevRatio - ratio >= 0.15 && ratio >= mBreakThreshold) {
                int leftVelocity = (int) (mLeftVelocity * ratio);
                BluetoothLeManager.getInstance().requestChangeWheelVelocity(leftVelocity, leftVelocity);
                mPrevRatio = ratio;
                mCurrentPosition += pos;
                //System.out.println("val: + " + ratio + ", mSum: " + mCurrentPosition);

                // wait for changing velocity
                while (true) {
                    StatusData status1 = BluetoothLeManager.getInstance().getCurrentStatusData();
                    if(status1 == null) return false;
                    if (leftVelocity == status1.getLeftWheelVelocity()) {
                        break;
                    }
                    try { Thread.sleep(1); }catch(InterruptedException e) { }
                }

            } else if (mCurrentPosition + pos >= mDistanceCm) {
                //System.out.println("finish Position: " + (pos + mCurrentPosition));
                return false;
            }
        }

        return true;
    }

    @Override
    protected void operate() {
        // wait for initializing status data.
        while(true) {
            StatusData status = BluetoothLeManager.getInstance().getCurrentStatusData();
            if(status == null) return;
            if(status.getLeftEncPosition() == 0 && status.getRightEncPosition() == 0) break;
            try { Thread.sleep(1); }catch(InterruptedException e) { }
        }

        BluetoothLeManager.getInstance().requestChangeWheelVelocity(mLeftVelocity, mRightVelocity);
    }

    @Override
    protected boolean detect() {
        return false;
    }

    @Override
    protected void stop() {
        BluetoothLeManager.getInstance().requestChangeWheelVelocity(0, 0);
    }

}
