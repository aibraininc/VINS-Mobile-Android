package com.aibrain.tyche.bluetoothle.drive;

import com.aibrain.tyche.bluetoothle.constants.Mode;

/**
 * Created by SichangYang on 2017. 3. 20..
 */

public class MoveDrive extends Drive {

    private int mDistance = 0;
    private int mVelocity = 0;

    public MoveDrive() {
        mMode = Mode.ENCODER;
    }

    public void setDistance(int centimeter) {
        mDistance = centimeter;
    }

    public int getDistance() {
        return mDistance;
    }

    public void setVelocity(int velocity) {
        mVelocity = velocity;
    }

    public int getVelocity() {
        return mVelocity;
    }

}
