package com.aibrain.tyche.bluetoothle.executor;

/**
 * Created by SichangYang on 2017. 3. 20..
 */

abstract public class EncoderBaseExecutor extends Executor {

    protected abstract void operate();
    protected abstract boolean detect();
    protected abstract boolean check();
    protected abstract void stop();

    @Override
    protected void doJob() {
        operate();

        while(check()) {
            if(isCanceled() || detect()) {
                break;
            }
            try { Thread.sleep(1); }catch(InterruptedException e) { e.printStackTrace(); }
        }

        stop();
    }

}
