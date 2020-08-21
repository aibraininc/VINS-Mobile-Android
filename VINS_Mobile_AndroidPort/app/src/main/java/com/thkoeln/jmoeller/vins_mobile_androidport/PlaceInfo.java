package com.thkoeln.jmoeller.vins_mobile_androidport;

public class PlaceInfo {

    PlaceInfo() {

    }

    PlaceInfo(int id, String name, float x, float y, float z) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    int id = 0;
    String name = "undefined";
    float x = 0;
    float y = 0;
    float z = 0;


    float calculateDistance(PlaceInfo another) {
        float distance = (x-another.x)*(x-another.x) + (y-another.y)*(y-another.y) + (z-another.z)*(z-another.z);
        return (float) Math.sqrt(distance);
    }

    float calculateDistance(float _x, float _y, float _z) {
        float distance = (x-_x)*(x-_x) + (y-_y)*(y-_y) + (z-_z)*(z-_z);
        return (float) Math.sqrt(distance);
    }

    float calculateAngle() {
        return (float) Math.atan(y/x);
    }

    float calculateAngle(float _x, float _y) {
        double angle1 = Math.atan(_y/_x);
        double angle2 = Math.atan(y/x);

        return (float) (180.0* (angle1 - angle2)/3.141592);
    }
}
