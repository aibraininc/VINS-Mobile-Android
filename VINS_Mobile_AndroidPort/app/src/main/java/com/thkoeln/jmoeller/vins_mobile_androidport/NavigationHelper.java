package com.thkoeln.jmoeller.vins_mobile_androidport;

import android.widget.Toast;

import java.util.ArrayList;

public class NavigationHelper {

    ArrayList<PlaceInfo> placeInfos;

    NavigationHelper(){
        this.placeInfos = new ArrayList<PlaceInfo>();
    }


    // 각도 계산하기
    // 모두 같은 방향으로 각도 계산함.
    public float calculateAngle(float x, float y) {
        float angle_2 = (float) (180*Math.atan(y/x)/3.141592);
        if(x>0 && y>0) {
            angle_2 = 90+angle_2;
        }
        else if(x<0 && y>0) {
            angle_2 = -90+angle_2;
        }
        else if(x>0 && y<0){
            angle_2 = 90 + angle_2;
        }
        else if(x<0 && y<0){
            angle_2 = -1* ( 90 - angle_2);
        }
        return angle_2;
    }


    // 장소 저장하기, 이름, 그리고 위치로 저장함.
    void addPlace(String name, float x, float y, float z){
        String[] name_array = name.split("여기는 ");
        if(name_array.length >1) {
            int length = placeInfos.size();
            PlaceInfo place = new PlaceInfo(length,name_array[1],x,y,z);
            placeInfos.add(place);
        }
    }

    // 특정 id의 장소 업데이트하기
    void updatePlace(int id, float x, float y, float z) {
        int idx = -1;
        for(int i =0; i< placeInfos.size();i++) {
            if(id == placeInfos.get(i).id)
                idx = i;
        }
        if(idx != -1) {
            placeInfos.get(idx).x = x;
            placeInfos.get(idx).y = y;
            placeInfos.get(idx).z = z;
        }
    }

    // text로 장소찾기
    int searchPlaceByName(String text){
        if(placeInfos.size() == 0)
            return -1;
        int idx = 0;
        for(int i =0; i< placeInfos.size();i++) {

            if(text.contains(placeInfos.get(i).name) ) {
                idx = i;
            }
        }
        return idx;
    }

    // x,y,z와 가장 가까운 장소 찾기
    PlaceInfo searchPlace(float x, float y, float z){
        if(placeInfos.size() == 0)
            return new PlaceInfo();
        int idx = 0;
        float minDistance = 100;
        for(int i =0; i< placeInfos.size();i++) {
            float distance = placeInfos.get(i).calculateDistance(x,y,z);
            if(distance < minDistance) {
                minDistance = distance;
                idx = i;
            }
        }
        return placeInfos.get(idx);
    }
}