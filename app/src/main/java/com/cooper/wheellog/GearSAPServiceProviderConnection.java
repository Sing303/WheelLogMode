package com.cooper.wheellog;

import android.util.Log;

import com.samsung.android.sdk.accessory.SASocket;

public class GearSAPServiceProviderConnection extends SASocket
{
    public final static String TAG = "SAPServiceProvider";
    static int nextID = 1;
    private int connectionID;
    private GearService mParent;

    public GearSAPServiceProviderConnection()
    {
        super(GearSAPServiceProviderConnection.class.getName());
        connectionID = ++nextID;
        Log.d(TAG, "GearSAPServiceProviderConnection");
    }

    public void setParent(GearService gearService)
    {
        mParent = gearService;
        Log.d(TAG, "Set Parent");
    }

    @Override
    protected void onServiceConnectionLost(int reason)
    {
        if (mParent != null)
        {
            mParent.removeConnection(this);
        }
        Log.d(TAG, "Set OnServiceConnectionLost");
    }

    @Override
    public void onReceive(int channelID, byte[] data)
    {
        Log.d(TAG, "OnReceive");
    }

    @Override
    public void onError(int channelID, String errorString, int errorCode)
    {
        Log.e(TAG, "ERROR:" + errorString + " | " + errorCode);
    }
}