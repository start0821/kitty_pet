package com.geoape.backgroundlocationexample;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Address;
import android.location.Location;
import android.location.LocationManager;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.renderscript.Sampler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.Console;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.ValueEventListener;


public class BackgroundService extends Service {
    private final LocationServiceBinder binder = new LocationServiceBinder();
    private final String TAG = "BackgroundService";
    private LocationListener mLocationListener;
    private LocationListener sLocationListener;
    private LocationManager mLocationManager;
    private NotificationManager notificationManager;

//    특정 시간에 서버에 데이터 전송

//    activity list
    private final ArrayList<String> activityList = new ArrayList<String>(){{
        add("movie");
        add("stadium");
        add("travel");
        add("hair");
        add("shopping");
        add("drink");
        add("sick");
        add("cafe");
        add("dine_out");
        add("fitness");
        add("dance");
        add("sing");
        add("music");
        add("study");
}};

//    get location using latitude and longitude
    private Geocoder mGeocoder;
//TODO: 업데이트 간격 바꾸어야함
    private final int LOCATION_INTERVAL = 0;
    private final int LOCATION_DISTANCE = 0;

    DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
    DatabaseReference Place = mDatabase.child("Users").child(getUserId()).child("Place");
    DatabaseReference UNKNOWN = mDatabase.child("Users").child(getUserId()).child("UNKNOWN");
    DatabaseReference TodayVisit = mDatabase.child("Users").child(getUserId()).child("TodayVisit");

//  place 변수
    ArrayList<Place> PlaceList = new ArrayList<>();
//    UNKNOWN 리스트
    ArrayList<String> unknownList = new ArrayList<>();


    //   오늘 한 활동 리스트
    Map<String, Boolean> todayActivity = new HashMap<>();
//   방문한 곳 리스트
    ArrayList<String> todayVisitedPlace = new ArrayList<>();
// 오늘 방문한 등록되지 않은 곳 리스트
    ArrayList<String> todayUnregisteredPlace = new ArrayList<>();

    // 위치추적 정보 처리 변수
    // 산책하고 있는지
    private boolean isWalking;
    // 분 단위
    private int stayingTime;
    private String lastStayedAddress;
    private String lastvisitedAddress;

    private String LastAccessDate;

    //todo: user 아이디 받는 방법 구상
    private String getUserId(){
        return "UserA";
    }

    private static class Place{
        public String maddress = null;
        public String mactivity = null;

        Place(){

        }

        Place(String address, String activity){
            maddress = address;
            mactivity = activity;
        }

        Place(String address){
            maddress = address;
        }

        @Override
        public String toString() {
            return "address : " + maddress + "-" + "activity : " + mactivity;
        }
    }

    private Map<String, String> getTime(){
        HashMap<String,String> data = new HashMap<>();
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        data.put("hours",new SimpleDateFormat().format("hh"));
        data.put("minutes",new SimpleDateFormat().format("mm"));
        data.put("seconds",new SimpleDateFormat().format("ss"));
        return data;
    }

    private String getDate(){
        long now = System.currentTimeMillis();
        Date date = new Date(now);
        return new SimpleDateFormat("yyyyMMdd").format(date);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }


    private class LocationListener implements android.location.LocationListener
    {
        private Location lastLocation = null;
        private final String TAG = "LocationListener";
        private Location mLastLocation;

        public LocationListener(String provider)
        {
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location)
        {
            if(LastAccessDate != getDate()){
//                initToday();
            }
            mLastLocation = location;
            List<Address> mResultList = new ArrayList<>();
            try{
                 mResultList = mGeocoder.getFromLocation(
                         mLastLocation.getLatitude(),
                         mLastLocation.getLongitude(),
                        1
                );
            }catch(IOException e){
                e.printStackTrace();
                Log.d(TAG,"onComplete: 주소 변환 실패");
            }
            String detailedLocation = mResultList.get(0).getAddressLine(0);
            Log.i(TAG, "LocationChanged: "+detailedLocation);

//            Toast.makeText(getApplicationContext(), "LocationChanged: "+location.getLatitude()+" , " + location.getLongitude(), Toast.LENGTH_LONG).show();
            // Location 을 placelist와 확인하고 firstvisited에 넣고 activity list 수정함.
//            TODO: UPDATE LOCATION 으로 바꾸어야함.
            updatestayLocation(detailedLocation);
        }

        @Override
        public void onProviderDisabled(String provider)
        {
            Log.e(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider)
        {
            Log.e(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {
            Log.e(TAG, "onStatusChanged: " + status);
        }
    }

    private void updateLocation(String location){
        if(lastvisitedAddress == location){
            stayingTime += 5;
            if(stayingTime>30){
                if(isWalking==true){
                    updatestayLocation(location);
                    isWalking = false;
                }
            }
        }else{
            lastvisitedAddress = location;
            isWalking = true;
            stayingTime = 0;
        }
    }

    // 오래 머문 장소에 대해서 처음온 장소인지, + 머무른 장소로 파이어베이스에 데이터를 뿌려줌
    private void updatestayLocation(String location){
        String todayDate = getDate();
        if(lastStayedAddress==location){
            return;
        }
        boolean todayVisited = false;
        boolean isRegistered = false;
        for(String place : todayVisitedPlace){
            if(place==location){
                todayVisited=true;
                break;
            }
        }
        if (todayVisited == false) {// 오늘 방문했던 곳이 아니라면
            todayVisitedPlace.add(location);
            for(Place place : PlaceList){
                // 해야할거 unknown으로 추가할지, 오늘 한일에 추가하기, 오늘 unknown에 추가하기
                Log.d(">>>>>>>>>> 확인하는 부분",place.maddress + " , " + location);
                if(location.equals(place.maddress)){ // 지금 방문한 곳이 카테고리에 있다면
                    isRegistered = true;
                    if(todayActivity.get(place.mactivity)==false){
                        todayActivity.put(place.mactivity,true);
                        TodayVisit.child(getDate()).child(place.mactivity).setValue("true");

                    }
                    break;
                }
            }
            if(isRegistered == false){// 등록되지 않은 장소라면
                for(String placename:unknownList){
                    Log.d(">>>> 등록되지 않은 장소", placename +", "+location);
                    if(location.equals(placename)){
                        isRegistered=true;
                        Log.d(">>>> 들어옴!!", placename +", "+location);
                        break;
                    }
                }
                if(isRegistered==false){ // UNKNOWN데이터베이스에 없다면 데이터베이스에 업로드
                    String key_value = unknownList.size()+"";
                    UNKNOWN.child(key_value).setValue(location);
                }
                Log.d(">>>>>>>>>>>>> 오늘 등록되지 않은 장소 ",todayUnregisteredPlace.toString());
                TodayVisit.child(getDate()+"_UNKNOWN").child(todayUnregisteredPlace.size()+"").setValue(location);
                todayUnregisteredPlace.add(location);
            }
        }
        lastStayedAddress = location;
    }

    //  하루가 지나가는 걸 어떻게 탐지 할 지 구현 => 지속적으로 콜백되는 함수들을 이용해서 직전에 실행했을 때의 date와 지금의 date가 같은지 확인하고 다르면 inittoday함수를 실행하도록 함.
    private void initToday(){
        todayVisitedPlace.clear();
        todayUnregisteredPlace.clear();
        for(Map.Entry<String,Boolean> entry :todayActivity.entrySet()){
            todayActivity.put(entry.getKey(),false);
            TodayVisit.child(getDate()).child(entry.getKey()).setValue(false);
        }
        lastStayedAddress = null;
        lastvisitedAddress = null;

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        super.onStartCommand(intent, flags, startId);
        LastAccessDate = getDate();
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate()
    {
        initToday();
        Log.i(TAG, "onCreate");
        mGeocoder = new Geocoder(getApplicationContext());
        startForeground(12345678, getNotification());
//        firebase 데이터 수정 될때마다 place list 수정
        Place.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(LastAccessDate != getDate())
//                    initToday();
                PlaceList.clear();
                for(DataSnapshot placeData : dataSnapshot.getChildren()){
                    Place place = new Place(placeData.getKey().replaceAll("\"",""),placeData.getValue().toString().replaceAll("\"",""));
                    PlaceList.add(place);
                }
                Log.d(TAG,PlaceList.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        UNKNOWN.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(LastAccessDate != getDate())
//                    initToday();
                unknownList.clear();
                for(DataSnapshot placeData : dataSnapshot.getChildren()){
                    String place = placeData.getValue().toString().replaceAll("\"","");
                    unknownList.add(place);
                }
                Log.d(TAG,PlaceList.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        //today activity 초기화
        for(String activity:activityList){
            todayActivity.put(activity,false);
        }

    }




    @Override
    public void onDestroy()
    {
        super.onDestroy();
        if (mLocationManager != null) {
            try {
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception ex) {
                Log.i(TAG, "fail to remove location listners, ignore", ex);
            }
        }
    }

    private void initializeLocationManager() {
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    public void startTracking() {
        initializeLocationManager();
        notification();
        mLocationListener = new LocationListener(LocationManager.GPS_PROVIDER);
        sLocationListener = new LocationListener(LocationManager.NETWORK_PROVIDER);

        try {
            mLocationManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener );
            mLocationManager.requestLocationUpdates( LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE, mLocationListener );

        } catch (java.lang.SecurityException ex) {
             Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
             Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }

    }

    public void stopTracking() {
        this.onDestroy();
    }

    private Notification getNotification() {

        NotificationChannel channel = new NotificationChannel("channel_01", "My Channel", NotificationManager.IMPORTANCE_DEFAULT);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Notification.Builder builder = new Notification.Builder(getApplicationContext(), "channel_01").setAutoCancel(true);
        return builder.build();
    }

    private Notification getNotification2() {

        NotificationChannel channel = new NotificationChannel("channel_02", "My Channel2", NotificationManager.IMPORTANCE_DEFAULT);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Notification.Builder builder = new Notification.Builder(getApplicationContext(), "channel_02").setAutoCancel(true);
        return builder.build();
    }

    
    public class LocationServiceBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }


    public void notification() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("MainActivity");

        Intent myIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(""));
        PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(),0,myIntent,Intent.FILL_IN_CLIP_DATA);
        Context context = getApplicationContext();

        Notification.Builder builder;
            builder = new Notification.Builder(context)
                    .setContentTitle("T")
                    .setContentText("M")
                    .setContentIntent(pendingIntent)
                    .setDefaults(Notification.DEFAULT_SOUND)
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.ic_launcher_background);
        Notification notification = builder.build();
        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1,notification);

    }
}
