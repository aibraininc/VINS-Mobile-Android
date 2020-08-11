package com.aibrain.tyche.bluetoothle.packet;

import com.aibrain.tyche.bluetoothle.constants.Command;
import com.aibrain.tyche.bluetoothle.constants.Packet;

public class HeadlightBrightness {

    public static final byte LENGTH = 0x01;

    public byte[] getCommand(int brightness) {
        return formatPacket(brightness);
    }

    private byte[] formatPacket(int brightness) {

        byte[] packet = new byte[5];

        packet[0] = Packet.START;
        packet[1] = LENGTH;
        packet[2] = Command.HEADLIGHT_BRIGHTNESS;
        packet[3] = formatBrightness(brightness);
        packet[4] = Packet.END;

        return packet;
    }

    private byte formatBrightness(int brightness) {
        if(brightness > 100) {
            brightness = 100;
        }
        if(brightness < 0) {
            brightness = 0;
        }

        return (byte)brightness;
    }

}
