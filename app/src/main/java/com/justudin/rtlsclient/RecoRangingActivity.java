/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Perples, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.justudin.rtlsclient;

import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.perples.recosdk.RECOBeacon;
import com.perples.recosdk.RECOBeaconRegion;
import com.perples.recosdk.RECOErrorCode;
import com.perples.recosdk.RECORangingListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * RECORangingActivity class is to range regions in the foreground.
 *
 */
public class RecoRangingActivity extends RecoActivity implements RECORangingListener {

    private RecoRangingListAdapter mRangingListAdapter;
    private ListView mRegionListView;
    private TextView infoLoc,patientInfoValue;
    private ArrayList<RECOBeacon> mRangedBeaconsHere;
    GPSTracker gps;
    Socket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reco_ranging);
        infoLoc = (TextView)findViewById(R.id.locationInfo);
        patientInfoValue = (TextView)findViewById(R.id.patientInfoValue);
        mRangedBeaconsHere = new ArrayList<RECOBeacon>();
        try {
            // Open a socket to your web server
            socket = IO.socket(MainActivity.SERVER_URL);
            socket.connect();
        } catch (Exception e) {
            // Assume we can connect
        }

        //mRecoManager will be created here. (Refer to the RECOActivity.onCreate())
        //Set RECORangingListener (Required)

        mRecoManager.setRangingListener(this);

        /**
         * Bind RECOBeaconManager with RECOServiceConnectListener, which is implemented in RECOActivity
         * You SHOULD call this method to use monitoring/ranging methods successfully.
         * After binding, onServiceConenct() callback method is called.
         * So, please start monitoring/ranging AFTER the CALLBACK is called.
         *
         */
        mRecoManager.bind(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mRangingListAdapter = new RecoRangingListAdapter(this);
        mRegionListView = (ListView)findViewById(R.id.list_ranging);
        mRegionListView.setAdapter(mRangingListAdapter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.stop(mRegions);
        this.unbind();
    }

    private void unbind() {
        try {
            mRecoManager.unbind();
        } catch (RemoteException e) {
            Log.i("RECORangingActivity", "Remote Exception");
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceConnect() {
        Log.i("RECORangingActivity", "onServiceConnect()");
        mRecoManager.setDiscontinuousScan(MainActivity.DISCONTINUOUS_SCAN);
        //RECOBeaconRegion mRecoRegion1 = new RECOBeaconRegion(mProximityUuid, mMajor1, mMinor1, "Clothing section of the branch in San Francisco");
        this.start(mRegions);
        //Write the code when RECOBeaconManager is bound to RECOBeaconService
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<RECOBeacon> recoBeacons, RECOBeaconRegion recoRegion){
        Log.i("RECORangingActivity", "didRangeBeaconsInRegion() region: " + recoRegion.getUniqueIdentifier() + ", number of beacons ranged: " + recoBeacons.size());
        gps = new GPSTracker(RecoRangingActivity.this);
        double latitude = gps.getLatitude();
        double longitude = gps.getLongitude();
        Log.i("RECORangingActivity", "Long + Lat: " + longitude + " + " + latitude);
        String deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        String code = getIntent().getStringExtra("code");
        patientInfoValue.setText(code);
        if(recoBeacons.size() == 0){
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); //yyyy-MM-dd HH:mm:ss.SSS
            String sentAt = simpleDateFormat.format(new Date());
            infoLoc.setText("Outside Building");
            // Create a JSON embeded object
            JSONObject obj = new JSONObject();
            try{
                obj.put("deviceId", deviceId);
                obj.put("patientCode", code);
                obj.put("sentAt", sentAt);
                obj.put("location", "outside");
                obj.put("long", longitude);
                obj.put("lat", latitude);
            } catch (JSONException e){
                Log.d("JSONERROR", e.toString());
            }

            // Send it to the server
            socket.emit(MainActivity.SOCKET_CHANNEL, obj.toString());


        } else {
            for(int n=0; n < mRangedBeaconsHere.size(); n++){
                RECOBeacon rb = mRangedBeaconsHere.get(n);
                infoLoc.setText("Floor "+rb.getMinor());
            }
        }
        mRangingListAdapter.updateAllBeacons(recoBeacons);
        mRangingListAdapter.notifyDataSetChanged();
        updateAllBeaconsHere(recoBeacons);

        try {
            mRangingListAdapter.printBeacon(deviceId,code,longitude,latitude);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        //Write the code when the beacons in the region is received
    }

    @Override
    protected void start(ArrayList<RECOBeaconRegion> regions) {

        /**
         * There is a known android bug that some android devices scan BLE devices only once. (link: http://code.google.com/p/android/issues/detail?id=65863)
         * To resolve the bug in our SDK, you can use setDiscontinuousScan() method of the RECOBeaconManager.
         * This method is to set whether the device scans BLE devices continuously or discontinuously.
         * The default is set as FALSE. Please set TRUE only for specific devices.
         *
         * mRecoManager.setDiscontinuousScan(true);
         */

        for(RECOBeaconRegion region : regions) {
            try {
                //You can set scan period. The default is 1 second.
                mRecoManager.setScanPeriod(60000); // 1 minutes
                mRecoManager.startRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                Log.i("RECORangingActivity", "Remote Exception");
                e.printStackTrace();
            } catch (NullPointerException e) {
                Log.i("RECORangingActivity", "Null Pointer Exception");
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void stop(ArrayList<RECOBeaconRegion> regions) {
        for(RECOBeaconRegion region : regions) {
            try {
                mRecoManager.stopRangingBeaconsInRegion(region);
            } catch (RemoteException e) {
                Log.i("RECORangingActivity", "Remote Exception");
                e.printStackTrace();
            } catch (NullPointerException e) {
                Log.i("RECORangingActivity", "Null Pointer Exception");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onServiceFail(RECOErrorCode errorCode) {
        //Write the code when the RECOBeaconService is failed.
        //See the RECOErrorCode in the documents.
        return;
    }

    @Override
    public void rangingBeaconsDidFailForRegion(RECOBeaconRegion region, RECOErrorCode errorCode) {
        Log.i("RECORangingActivity", "error code = " + errorCode);
        //Write the code when the RECOBeaconService is failed to range beacons in the region.
        //See the RECOErrorCode in the documents.
        return;
    }

    public void updateBeaconHere(RECOBeacon beacon) {
        synchronized (mRangedBeaconsHere) {
            if(mRangedBeaconsHere.contains(beacon)) {
                mRangedBeaconsHere.remove(beacon);
            }
            mRangedBeaconsHere.add(beacon);
        }
    }

    public void updateAllBeaconsHere(Collection<RECOBeacon> beacons) {
        synchronized (beacons) {
            mRangedBeaconsHere = new ArrayList<RECOBeacon>(beacons);
        }
    }
}
