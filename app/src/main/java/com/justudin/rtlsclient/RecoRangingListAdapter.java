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

import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.perples.recosdk.RECOBeacon;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import io.socket.client.IO;
import io.socket.client.Socket;

public class RecoRangingListAdapter extends BaseAdapter {
    private ArrayList<RECOBeacon> mRangedBeacons;
    private LayoutInflater mLayoutInflater;
    Socket socket;

    public RecoRangingListAdapter(Context context) {
        super();
        mRangedBeacons = new ArrayList<RECOBeacon>();
        mLayoutInflater = LayoutInflater.from(context);
        try {
            // Open a socket to your web server
            socket = IO.socket(MainActivity.SERVER_URL);
            socket.connect();
        } catch (Exception e) {
            // Assume we can connect
        }
    }

    public void updateBeacon(RECOBeacon beacon) {
        synchronized (mRangedBeacons) {
            if(mRangedBeacons.contains(beacon)) {
                mRangedBeacons.remove(beacon);
            }
            mRangedBeacons.add(beacon);
        }
    }

    public void updateAllBeacons(Collection<RECOBeacon> beacons) {
        synchronized (beacons) {
            mRangedBeacons = new ArrayList<RECOBeacon>(beacons);
        }
    }

    public void printBeacon(String deviceId, String patientCode, double longitude, double latitude) throws JSONException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); //yyyy-MM-dd HH:mm:ss.SSS
        String sentAt = simpleDateFormat.format(new Date());

        // Create a JSON embeded object
        JSONObject obj = new JSONObject();
        obj.put("deviceId", deviceId);
        obj.put("patientCode", patientCode);
        obj.put("sentAt", sentAt);
        obj.put("long", longitude);
        obj.put("lat", latitude);
        JSONObject ble = new JSONObject();

        for(int n=0; n < mRangedBeacons.size(); n++){
            RECOBeacon rb = mRangedBeacons.get(n);
            obj.put("location", "Floor "+rb.getMinor());
            String proximityUuid = rb.getProximityUuid();
            ble.put("uuid", String.format("%s-%s-%s-%s-%s", proximityUuid.substring(0, 8), proximityUuid.substring(8, 12), proximityUuid.substring(12, 16), proximityUuid.substring(16, 20), proximityUuid.substring(20) ));
            ble.put("major", rb.getMajor());
            ble.put("minor", rb.getMinor());
            ble.put("power", rb.getTxPower());
            ble.put("rssi", rb.getRssi());
            ble.put("battery", rb.getBattery());
            ble.put("proximity", rb.getProximity().toString().replace("RECO",""));
            obj.put("distance", String.format("%.2f", rb.getAccuracy())+"m");
            // Send it to the server
            obj.put("bleinfo", ble);
            socket.emit("postmonitoringrtls", obj.toString());
            //Log.i("recoBeacon: ",rb.getProximityUuid());
        }
    }

    public void clear() {
        mRangedBeacons.clear();
    }

    @Override
    public int getCount() {
        return mRangedBeacons.size();
    }

    @Override
    public Object getItem(int position) {
        return mRangedBeacons.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;

        if(convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.item_ranging_beacon, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.recoProximityUuid = (TextView)convertView.findViewById(R.id.recoProximityUuid);
            viewHolder.recoMajor = (TextView)convertView.findViewById(R.id.recoMajor);
            viewHolder.recoMinor = (TextView)convertView.findViewById(R.id.recoMinor);
            viewHolder.recoTxPower = (TextView)convertView.findViewById(R.id.recoTxPower);
            viewHolder.recoRssi = (TextView)convertView.findViewById(R.id.recoRssi);
            viewHolder.recoBattery = (TextView)convertView.findViewById(R.id.recoBattery);
            viewHolder.recoProximity = (TextView)convertView.findViewById(R.id.recoProximity);
            viewHolder.recoAccuracy = (TextView)convertView.findViewById(R.id.recoAccuracy);
            viewHolder.locationInfo = (TextView)convertView.findViewById(R.id.locationInfo);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder)convertView.getTag();
        }

        RECOBeacon recoBeacon = mRangedBeacons.get(position);

        String proximityUuid = recoBeacon.getProximityUuid();

        viewHolder.recoProximityUuid.setText(String.format("%s-%s-%s-%s-%s", proximityUuid.substring(0, 8), proximityUuid.substring(8, 12), proximityUuid.substring(12, 16), proximityUuid.substring(16, 20), proximityUuid.substring(20) ));
        viewHolder.recoMajor.setText(recoBeacon.getMajor() + "");
        viewHolder.recoMinor.setText(recoBeacon.getMinor() + "");
        viewHolder.recoTxPower.setText(recoBeacon.getTxPower() + "");
        viewHolder.recoRssi.setText(recoBeacon.getRssi() + "");
        viewHolder.recoBattery.setText(recoBeacon.getBattery() + "");
        viewHolder.recoProximity.setText(recoBeacon.getProximity().toString().replace("RECO","") + "");
        viewHolder.recoAccuracy.setText(String.format("%.2f", recoBeacon.getAccuracy()));
        if(mRangedBeacons.size() == 0){
            viewHolder.locationInfo.setText("Outside Building");
        } else  {
            viewHolder.locationInfo.setText("Floor "+ recoBeacon.getMinor());
        }

        return convertView;
    }

    static class ViewHolder {
        TextView recoProximityUuid;
        TextView recoMajor;
        TextView recoMinor;
        TextView recoTxPower;
        TextView recoRssi;
        TextView recoBattery;
        TextView recoProximity;
        TextView recoAccuracy;
        TextView locationInfo;
    }

}
