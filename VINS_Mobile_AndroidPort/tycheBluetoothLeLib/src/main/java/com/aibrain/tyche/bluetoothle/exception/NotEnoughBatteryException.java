package com.aibrain.tyche.bluetoothle.exception;

/**
 * Created by SichangYang on 2017. 7. 19..
 */

public class NotEnoughBatteryException extends Exception {

    public NotEnoughBatteryException() {
        this("Tyche has not enough battery.");
    }

    public NotEnoughBatteryException(String msg) {
        super(msg);
    }

}
