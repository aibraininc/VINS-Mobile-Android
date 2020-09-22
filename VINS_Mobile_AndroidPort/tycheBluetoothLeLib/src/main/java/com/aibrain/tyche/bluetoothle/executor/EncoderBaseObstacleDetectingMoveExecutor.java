package com.aibrain.tyche.bluetoothle.executor;

import com.aibrain.tyche.bluetoothle.BluetoothLeManager;
import com.aibrain.tyche.bluetoothle.packet.receive.StatusData;

/**
 * Created by SichangYang on 2017. 3. 20..
 */

public class EncoderBaseObstacleDetectingMoveExecutor extends EncoderBaseMoveExecutor {

    private OnObstacleDetectedListener mOnObstacleDetectedListener;

    public EncoderBaseObstacleDetectingMoveExecutor(int distanceCm, int velocity,
                                                    OnObstacleDetectedListener listener) {
        super(distanceCm, velocity);
        mOnObstacleDetectedListener = listener;
    }

    @Override
    protected boolean detect() {
        if(mOnObstacleDetectedListener == null) {
            return false;
        }
        else {
            StatusData status = BluetoothLeManager.getInstance().getCurrentStatusData();
            if(status != null) {
                int leftVel = status.getLeftWheelVelocity();
                int rightVel = status.getRightWheelVelocity();
                if(leftVel>0 && rightVel>0) {
                    int distance = status.getDistance();
                    if(distance>0 && distance < /*Math.max(leftVel, rightVel)/3*/20) {
                        if(mOnObstacleDetectedListener != null) {
                            mOnObstacleDetectedListener.onDetected();
                        }
                        return true;
                    }
                }
            }

            return false;
        }
    }

}
