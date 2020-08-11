package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;
import com.aibrain.tyche.bluetoothle.constants.Velocity;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

/**
 * Created by SichangYang on 2017. 3. 20..
 */

public class EncoderBaseRotateExecutor extends EncoderBaseExecutor {

    private int mLeftVelocity = 0;
    private int mRightVelocity = 0;
    private int mPulseCount = 0;

    public EncoderBaseRotateExecutor(int angle) {
        if(angle > 0) {
            mLeftVelocity = Velocity.TURN;
            mRightVelocity = -Velocity.TURN;
        }
        else {
            mLeftVelocity = -Velocity.TURN;
            mRightVelocity = Velocity.TURN;
        }
        mPulseCount = (int)(Math.abs(angle) * 7.5 / 45);
    }

    @Override
    protected boolean check() {
        StatusData status = BluetoothLeManager.getInstance().getCurrentStatusData();
        if(status == null) return false;

        if(status.getLeftWheelVelocity() != 0 && status.getRightWheelVelocity() != 0) {
            int leftPos = status.getLeftEncPosition();
            int rightPos = status.getRightEncPosition();
            if (leftPos > mPulseCount && rightPos > mPulseCount) {
                //System.out.println("(" + leftPos + ", " + rightPos + ")");
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
