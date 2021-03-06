package com.cooper.wheellog;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Vibrator;

import com.cooper.wheellog.utils.Constants;
import com.cooper.wheellog.utils.Constants.ALARM_TYPE;
import com.cooper.wheellog.utils.Constants.WHEEL_TYPE;
import com.cooper.wheellog.utils.InMotionAdapter;
import com.cooper.wheellog.utils.NinebotAdapter;
import com.cooper.wheellog.utils.NinebotZAdapter;
import com.cooper.wheellog.utils.SettingsUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class WheelData
{
    private static final int TIME_BUFFER = 10;
    private static final int GRAPH_UPDATE_INTERVAL = 1000;
    private static final int RIDING_SPEED = 200;
    private static final double RATIO_GW = 0.875;
    private static final double KS18L_SCALER = 0.83;
    private static WheelData mInstance;
    private static AudioTrack audioTrack = null;
    public int mMaxLoadPercent;
    ArrayList<UserLog> mUserLog = new ArrayList<UserLog>();
    double mLeftKm = 0;
    double mStartVoltage = 0;
    double mStartVoltageDistance = 0;
    int mLastRestBattery = 0;
    double mLastRestVoltage = 0;
    private Timer ridingTimerControl;
    private BluetoothLeService mBluetoothLeService;
    private long graph_last_update_time;
    private ArrayList<String> xAxis = new ArrayList<>();
    private ArrayList<Float> currentAxis = new ArrayList<>();
    private ArrayList<Float> speedAxis = new ArrayList<>();
    private int mSpeed;
    private long mTotalDistance;
    private int mCurrent;
    private int mTemperature;
    private int mTemperature2;
    private double mAngle;
    private double mRoll;
    private int mMode;
    private int mBattery;
    private double mAverageBattery;
    private int mVoltage;
    private long mDistance;
    private long mUserDistance;
    private int mRideTime;
    private int mRidingTime;
    private int mLastRideTime;
    private double mTopSpeed;
    private int mVoltageSag;
    private int mFanStatus;
    private boolean mConnectionState = false;
    private boolean mNewWheelSettings = false;
    private boolean mKSAlertsAndSpeedupdated = false;
    private String mName = "Unknown";
    private String mModel = "Unknown";
    private String mModeStr = "Unknown";
    private String mBtName = "";
    private String mAlert = "";
    private String mVersion = "";
    private String mSerialNumber = "Unknown";
    private WHEEL_TYPE mWheelType = WHEEL_TYPE.Unknown;
    private long rideStartTime;
    private long mStartTotalDistance;

    /// Wheel Settings
    private boolean mWheelLightEnabled = false;
    private boolean mWheelLedEnabled = false;
    private boolean mWheelButtonDisabled = false;
    private int mWheelMaxSpeed = 0;
    private int mWheelSpeakerVolume = 50;
    private int mWheelTiltHorizon = 0;
    private boolean mAlarmsEnabled = false;
    private boolean mDisablePhoneVibrate = false;
    private boolean mDisablePhoneBeep = false;
    private int mAlarm1Speed = 0;
    private int mAlarm2Speed = 0;
    private int mAlarm3Speed = 0;
    private int mKSAlarm1Speed = 0;
    private int mKSAlarm2Speed = 0;
    private int mKSAlarm3Speed = 0;
    private int mAlarm1Battery = 0;
    private int mAlarm2Battery = 0;
    private int mAlarm3Battery = 0;
    private int mAlarmCurrent = 0;
    private int mAlarmTemperature = 0;
    private int mGotwayVoltageScaler = 0;
    private int mGotwayNegative = -1;
    private double mRotationSpeed = 70.0;
    private double mRotationVoltage = 84.00;
    private double mFirstPwm = 0.76;
    private double mSecondPwm = 0.78;
    private double mThirdPwm = 0.80;
    private double mAlarmFactor3 = 0.90;
    private int mAdvanceWarningSpeed = 0;
    private double mStartRecommendPwm = 0;
    private double mFinishRecommendPwm = 0;
    private long mLastPlayWarningSpeedTime = System.currentTimeMillis();
    private long mLastPlayRecommendSpeedTime = System.currentTimeMillis();
    private double mSpeedCorrectionFactor = 0;
    public int mBatteryCapacity = 0;
    private double mChargingPowerAmp = 0;
    private int mVoltageSpeedThreshold = 0;
    private double mVoltageThreshold = 0;
    private boolean mIsVoltageSpeedCalculating = false;
    private double mTiltBackVoltage = 0;
    private boolean mAlteredAlarms = false;
    private boolean mUseRatio = false;
    private boolean m18Lkm = true;
    private boolean mBetterPercents = false;
    private boolean mUseStopMusic = false;
    private boolean mSpeedAlarmExecuting = false;
    private boolean mCurrentAlarmExecuting = false;
    private boolean mTemperatureAlarmExecuting = false;
    private long mLowSpeedMusicTime = 0;
    private boolean mVeteran = false;
    private String protoVer = "";
    private int duration = 1;
    private int sampleRate = 44100;
    private int numSamples = duration * sampleRate;
    private short[] buffer = new short[numSamples];
    private int sfreq = 440;
    private long timestamp_raw;
    private long timestamp_last;

    static void initiate()
    {
        if (mInstance == null)
        {
            mInstance = new WheelData();
        }
        else
        {
            if (mInstance.ridingTimerControl != null)
            {
                mInstance.ridingTimerControl.cancel();
                mInstance.ridingTimerControl = null;
            }
        }

        mInstance.full_reset();
        mInstance.prepareTone(mInstance.sfreq);
        mInstance.startRidingTimerControl();
    }

    public static WheelData getInstance()
    {
        return mInstance;
    }

    void playBeep(ALARM_TYPE type)
    {
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, buffer.length,
                AudioTrack.MODE_STATIC);
        if (type.getValue() < 4)
        {
            audioTrack.write(buffer, sampleRate / 20, ((type.getValue()) * sampleRate) / 20);

        }
        else if (type == ALARM_TYPE.CURRENT)
        {
            audioTrack.write(buffer, sampleRate * 3 / 10, (2 * sampleRate) / 20);

        }
        else
        {
            audioTrack.write(buffer, sampleRate * 3 / 10, (6 * sampleRate) / 10);
        }

        audioTrack.play();

    }

    private void prepareTone(int freq)
    {
        for (int i = 0; i < numSamples; ++i)
        {
            double originalWave = Math.sin(2 * Math.PI * freq * i / sampleRate);
            double harmonic1 = 0.5 * Math.sin(2 * Math.PI * 2 * freq * i / sampleRate);
            double harmonic2 = 0.25 * Math.sin(2 * Math.PI * 4 * freq * i / sampleRate);
            double secondWave = Math.sin(2 * Math.PI * freq * 1.34F * i / sampleRate);
            double thirdWave = Math.sin(2 * Math.PI * freq * 2.0F * i / sampleRate);
            double fourthWave = Math.sin(2 * Math.PI * freq * 2.68F * i / sampleRate);
            if (i <= (numSamples * 3) / 10)
            {
                buffer[i] = (short) ((originalWave + harmonic1 + harmonic2) * (Short.MAX_VALUE));
            }
            else if (i < (numSamples * 3) / 5)
            {
                buffer[i] = (short) ((originalWave + secondWave) * (Short.MAX_VALUE));
            }
            else
            {
                buffer[i] = (short) ((thirdWave + fourthWave) * (Short.MAX_VALUE));
            }
        }
    }

    public void startRidingTimerControl()
    {
        TimerTask timerTask = new TimerTask()
        {
            @Override
            public void run()
            {
                if (mConnectionState && (getCorrectedSpeed() > RIDING_SPEED)) mRidingTime += 1;
            }
        };
        ridingTimerControl = new Timer();
        ridingTimerControl.scheduleAtFixedRate(timerTask, 0, 1000);
    }

    double getSpeed()
    {
        return (mSpeed / 10) * mSpeedCorrectionFactor;
    }

    public int getSafeLoadPercent()
    {
        return (int) ((getCorrectedSpeed() / (mRotationSpeed / mRotationVoltage * mVoltage)) * 100);
    }

    int mAvgSafeLoadPercentRes;
    int mAvgSafeLoadPercentResCount;
    public int getAvgLoadPercent()
    {
        if (getCorrectedSpeed() > RIDING_SPEED)
        {
            mAvgSafeLoadPercentResCount++;
            mAvgSafeLoadPercentRes += getSafeLoadPercent();
        }

        if (mAvgSafeLoadPercentResCount <= 0)
            return 0;

        return mAvgSafeLoadPercentRes / mAvgSafeLoadPercentResCount;
    }

    boolean getWheelLight()
    {
        return mWheelLightEnabled;
    }

    boolean getWheelLed()
    {
        return mWheelLedEnabled;
    }

    boolean getWheelHandleButton()
    {
        return mWheelButtonDisabled;
    }

    int getWheelMaxSpeed()
    {
        return mWheelMaxSpeed;
    }

    int getKSAlarm1Speed()
    {
        return mKSAlarm1Speed;
    }

    int getKSAlarm2Speed()
    {
        return mKSAlarm2Speed;
    }

    int getKSAlarm3Speed()
    {
        return mKSAlarm3Speed;
    }

    int getSpeakerVolume()
    {
        return mWheelSpeakerVolume;
    }

    int getPedalsPosition()
    {
        return mWheelTiltHorizon;
    }

    public boolean isPrefReceived()
    {
        return mKSAlertsAndSpeedupdated;
    }

    public void setBtName(String btName)
    {
        mBtName = btName;
    }

    public void updateLight(boolean enabledLight)
    {
        if (mWheelLightEnabled != enabledLight)
        {
            mWheelLightEnabled = enabledLight;
            InMotionAdapter.getInstance().setLightState(enabledLight);
        }
    }

    public void updateLed(boolean enabledLed)
    {
        if (mWheelLedEnabled != enabledLed)
        {
            mWheelLedEnabled = enabledLed;
            InMotionAdapter.getInstance().setLedState(enabledLed);
        }
    }

    public void updatePedalsMode(int pedalsMode)
    {
        if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            switch (pedalsMode)
            {
                case 0:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("h".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
                case 1:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("f".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
                case 2:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("s".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
            }
        }

        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            byte[] data = new byte[20];
            data[0] = (byte) 0xAA;
            data[1] = (byte) 0x55;
            data[2] = (byte) pedalsMode;
            data[3] = (byte) 0xE0;
            data[16] = (byte) 0x87;
            data[17] = (byte) 0x15;
            data[18] = (byte) 0x5A;
            data[19] = (byte) 0x5A;
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
        }
    }

    public void updateLightMode(int lightMode)
    {
        if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            switch (lightMode)
            {
                case 0:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("E".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
                case 1:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("Q".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
                case 2:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("T".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
            }
        }

        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            byte[] data = new byte[20];
            data[0] = (byte) 0xAA;
            data[1] = (byte) 0x55;
            data[2] = (byte) (lightMode + 0x12);
            data[3] = (byte) 0x01;
            data[16] = (byte) 0x73;
            data[17] = (byte) 0x14;
            data[18] = (byte) 0x5A;
            data[19] = (byte) 0x5A;
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
        }
    }

    public void updateStrobe(int strobeMode)
    {
        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            byte[] data = new byte[20];
            data[0] = (byte) 0xAA;
            data[1] = (byte) 0x55;
            data[2] = (byte) strobeMode;
            data[16] = (byte) 0x53;
            data[17] = (byte) 0x14;
            data[18] = (byte) 0x5A;
            data[19] = (byte) 0x5A;
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
        }
    }

    public void updateLedMode(int ledMode)
    {
        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            byte[] data = new byte[20];
            data[0] = (byte) 0xAA;
            data[1] = (byte) 0x55;
            data[2] = (byte) ledMode;
            data[16] = (byte) 0x6C;
            data[17] = (byte) 0x14;
            data[18] = (byte) 0x5A;
            data[19] = (byte) 0x5A;
            mBluetoothLeService.writeBluetoothGattCharacteristic(data);
        }
    }

    public void updateAlarmMode(int alarmMode)
    {
        if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            switch (alarmMode)
            {
                case 0:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("u".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);

                    break;
                case 1:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("i".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
                case 2:
                    mBluetoothLeService.writeBluetoothGattCharacteristic("o".getBytes());
                    new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 100);
                    break;
            }
        }
    }

    public void updateCalibration()
    {
        if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            mBluetoothLeService.writeBluetoothGattCharacteristic("c".getBytes());
            new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("y".getBytes()), 300);
        }
    }


    public void updateHandleButton(boolean enabledButton)
    {
        if (mWheelButtonDisabled != enabledButton)
        {
            mWheelButtonDisabled = enabledButton;
            InMotionAdapter.getInstance().setHandleButtonState(enabledButton);
        }
    }

    public void updateMaxSpeed(int wheelMaxSpeed)
    {
        if (mWheelType == WHEEL_TYPE.INMOTION)
        {
            if (mWheelMaxSpeed != wheelMaxSpeed)
            {
                mWheelMaxSpeed = wheelMaxSpeed;
                InMotionAdapter.getInstance().setMaxSpeedState(wheelMaxSpeed);
            }
        }

        if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            final byte[] hhh = new byte[1];
            final byte[] lll = new byte[1];
            if (wheelMaxSpeed != 0)
            {
                int wheelMaxSpeed2 = wheelMaxSpeed;
                hhh[0] = (byte) ((wheelMaxSpeed2 / 10) + 0x30);
                lll[0] = (byte) ((wheelMaxSpeed2 % 10) + 0x30);
                mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes());
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("W".getBytes()), 100);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("Y".getBytes()), 200);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic(hhh), 300);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic(lll), 400);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 500);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 600);
            }
            else
            {
                mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes());
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("\"".getBytes()), 100);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 200);
                new Handler().postDelayed(() -> mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes()), 300);

            }
        }

        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            if (mWheelMaxSpeed != wheelMaxSpeed)
            {
                mWheelMaxSpeed = wheelMaxSpeed;
                updateKSAlarmAndSpeed();
            }
        }
    }

    public void updateKSAlarmAndSpeed()
    {
        byte[] data = new byte[20];
        data[0] = (byte) 0xAA;
        data[1] = (byte) 0x55;
        data[2] = (byte) mKSAlarm1Speed;
        data[4] = (byte) mKSAlarm2Speed;
        data[6] = (byte) mKSAlarm3Speed;
        data[8] = (byte) mWheelMaxSpeed;
        data[16] = (byte) 0x85;

        if ((mWheelMaxSpeed | mKSAlarm3Speed | mKSAlarm2Speed | mKSAlarm1Speed) == 0)
        {
            // Request speed & alarm values from wheel
            data[16] = (byte) 0x98;
        }

        data[17] = (byte) 0x14;
        data[18] = (byte) 0x5A;
        data[19] = (byte) 0x5A;
        mBluetoothLeService.writeBluetoothGattCharacteristic(data);
    }

    public void updateKSAlarm1(int wheelKSAlarm1)
    {
        if (mKSAlarm1Speed != wheelKSAlarm1)
        {
            mKSAlarm1Speed = wheelKSAlarm1;
            updateKSAlarmAndSpeed();
        }
    }

    public void updateKSAlarm2(int wheelKSAlarm2)
    {
        if (mKSAlarm2Speed != wheelKSAlarm2)
        {
            mKSAlarm2Speed = wheelKSAlarm2;
            updateKSAlarmAndSpeed();
        }
    }

    public void updateKSAlarm3(int wheelKSAlarm3)
    {
        if (mKSAlarm3Speed != wheelKSAlarm3)
        {
            mKSAlarm3Speed = wheelKSAlarm3;
            updateKSAlarmAndSpeed();
        }
    }

    public void updateSpeakerVolume(int speakerVolume)
    {
        if (mWheelSpeakerVolume != speakerVolume)
        {
            mWheelSpeakerVolume = speakerVolume;
            InMotionAdapter.getInstance().setSpeakerVolumeState(speakerVolume);
        }
    }

    public void updatePedals(int pedalAdjustment)
    {
        if (mWheelTiltHorizon != pedalAdjustment)
        {
            mWheelTiltHorizon = pedalAdjustment;
            InMotionAdapter.getInstance().setTiltHorizon(pedalAdjustment);
        }
    }

    public int getTemperature()
    {
        return mTemperature / 100;
    }

    public int getTemperature2()
    {
        return mTemperature2 / 100;
    }

    public double getAngle()
    {
        return mAngle;
    }

    public double getRoll()
    {
        return mRoll;
    }

    public int getBatteryLevel()
    {
        return mBattery;
    }

    int getFanStatus()
    {
        return mFanStatus;
    }

    boolean isConnected()
    {
        return mConnectionState;
    }

    void setConnected(boolean connected)
    {
        mConnectionState = connected;
        Timber.i("State %b", connected);
    }

    String getVersion()
    {
        return mVersion;
    }

    int getMode()
    {
        return mMode;
    }

    WHEEL_TYPE getWheelType()
    {
        return mWheelType;
    }

    boolean isVeteran()
    {
        return mVeteran;
    }

    String getName()
    {
        return mName;
    }

    String getModel()
    {
        return mModel;
    }

    String getModeStr()
    {
        return mModeStr;
    }

    String getLeftKm()
    {
        if (mLeftKm <= 0)
            return "Calculation...";

        return getSpeed() == 0
                ? String.format(Locale.US, "~%.2f km", mLeftKm)
                : String.format(Locale.US, "~%.2f km *", mLeftKm);
    }

    String getChargeTime()
    {
        double maxVoltage = 67.2;
        double minVoltage = mTiltBackVoltage;
        switch (mGotwayVoltageScaler)
        {
            case 1:
                maxVoltage = 84.0;
                minVoltage = mTiltBackVoltage;
                break;
            case 2:
                maxVoltage = 100.8;
                minVoltage = mTiltBackVoltage;
                break;
        }

        if (isVeteran())
        {
            maxVoltage = 100.8;
            minVoltage = 75.6;
        }

        double whInOneV = mBatteryCapacity / (maxVoltage - minVoltage);
        double needToMax = maxVoltage - mLastRestVoltage;
        double needToMaxInWh = needToMax * whInOneV;
        double chargePower = maxVoltage * mChargingPowerAmp;
        int chargeTime = (int) (needToMaxInWh / chargePower * 60);
        return getSpeed() == 0
                ? String.format(Locale.US, "~%d min", chargeTime)
                : String.format(Locale.US, "~%d min *", chargeTime);
    }

    String getComfortVoltageCost()
    {
        double comfortVoltageCost = mVoltageThreshold;
//        if (comfortVoltageCost <= 0 && LoggingService.mLocation == null)
  //          return "Calculation... (GPS must be on)";
    //    if (comfortVoltageCost <= 0 && LoggingService.mLocation != null)
      //      return "Calculation...";

        if (comfortVoltageCost <= 0)
            return String.format(Locale.US, "~%.2fV (%.2fV, %.2fV) *", comfortVoltageCost, mostMinVoltage, mostPercentStep);
        else
            return String.format(Locale.US, "~%.2fV (%.2fV, %.2fV)", comfortVoltageCost, mostMinVoltage, mostPercentStep);
    }

    String getLastRestBattery()
    {
        if (mLastRestVoltage <= 0 && mLastRestBattery <= 0)
            return "Calculation...";

        return String.format(Locale.US, "%d %% (%.2f V)", mLastRestBattery, mLastRestVoltage);
    }

    int getLastRestBatteryValue()
    {
        return mLastRestBattery;
    }

    String getAlert()
    {
        String nAlert = mAlert;
        mAlert = "";
        return nAlert;
    }

    String getSerial()
    {
        return mSerialNumber;
    }

    int getRideTime()
    {
        return mRideTime;
    }

    double getAverageSpeedDouble()
    {
        if (mTotalDistance != 0 && mRideTime != 0)
        {
            return (((mTotalDistance - mStartTotalDistance) * 3.6) / (mRideTime + mLastRideTime));
        }
        else
        {
            return 0.0;
        }
    }

    double getAverageRidingSpeedDouble()
    {
        if (mTotalDistance != 0 && mRidingTime != 0)
        {
            return (((mTotalDistance - mStartTotalDistance) * 3.6) / mRidingTime);
        }
        else
        {
            return 0.0;
        }
    }

    String getRideTimeString()
    {
        int currentTime = mRideTime + mLastRideTime;
        long hours = TimeUnit.SECONDS.toHours(currentTime);
        long minutes = TimeUnit.SECONDS.toMinutes(currentTime) -
                TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(currentTime));
        long seconds = TimeUnit.SECONDS.toSeconds(currentTime) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(currentTime));
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    String getRidingTimeString()
    {
        long hours = TimeUnit.SECONDS.toHours(mRidingTime);
        long minutes = TimeUnit.SECONDS.toMinutes(mRidingTime) -
                TimeUnit.HOURS.toMinutes(TimeUnit.SECONDS.toHours(mRidingTime));
        long seconds = TimeUnit.SECONDS.toSeconds(mRidingTime) -
                TimeUnit.MINUTES.toSeconds(TimeUnit.SECONDS.toMinutes(mRidingTime));
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    double getSpeedDouble()
    {
        return (mSpeed / 100.0) * mSpeedCorrectionFactor;
    }

    double getVoltageDouble()
    {
        return mVoltage / 100.0;
    }

    double getVoltageSagDouble()
    {
        return mVoltageSag / 100.0;
    }

    double getPowerDouble()
    {
        if (mWheelType == WHEEL_TYPE.GOTWAY)
            return -1;

        return (mCurrent * mVoltage) / 10000.0;
    }

    double getCurrentDouble()
    {
        return mCurrent / 100.0;
    }

    double getTopSpeed()
    {
        return mTopSpeed;
    }

    private void setTopSpeed(double topSpeed)
    {
        if (topSpeed > mTopSpeed)
        {
            mTopSpeed = topSpeed;
        }
    }

    double getTopSpeedDouble()
    {
        return mTopSpeed / 100.0;
    }

    int getDistance()
    {
        return (int) (mTotalDistance - mStartTotalDistance);
    }

    private void setDistance(long distance)
    {
        if (mStartTotalDistance == 0 && mTotalDistance != 0)
        {
            mStartTotalDistance = mTotalDistance;
        }

        mDistance = distance;
    }

    int getAlarm()
    {
        int alarm = 0;
        if (mSpeedAlarmExecuting)
        {
            alarm = alarm | 0x01;
        }
        if (mTemperatureAlarmExecuting)
        {
            alarm = alarm | 0x04;
        }
        if (mCurrentAlarmExecuting)
        {
            alarm = alarm | 0x02;
        }
        return alarm;
    }

    long getWheelDistance()
    {
        return mDistance;
    }

    public double getWheelDistanceDouble()
    {
        return mDistance / 1000.0;
    }

    public double getUserDistanceDouble()
    {
        if (mUserDistance == 0 && mTotalDistance != 0)
        {
            Context mContext = mBluetoothLeService.getApplicationContext();
            mUserDistance = SettingsUtil.getUserDistance(mContext, mBluetoothLeService.getBluetoothDeviceAddress());
            if (mUserDistance == 0)
            {
                SettingsUtil.setUserDistance(mContext, mBluetoothLeService.getBluetoothDeviceAddress(), mTotalDistance);
                mUserDistance = mTotalDistance;
            }
        }
        return (mTotalDistance - mUserDistance) / 1000.0;
    }

    public String getMac()
    {
        return mBluetoothLeService.getBluetoothDeviceAddress();
    }

    public long getTimeStamp()
    {
        return timestamp_last;
    }

    public void resetUserDistance()
    {
        if (mTotalDistance != 0)
        {
            Context mContext = mBluetoothLeService.getApplicationContext();
            SettingsUtil.setUserDistance(mContext, mBluetoothLeService.getBluetoothDeviceAddress(), mTotalDistance);
            mUserDistance = mTotalDistance;
        }

    }

    public void resetTopSpeed()
    {
        mTopSpeed = 0;
    }

    public void resetVoltageSag()
    {
        Timber.i("Sag WD");
        mVoltageSag = 20000;
    }

    public void setBetterPercents(boolean betterPercents)
    {

        mBetterPercents = betterPercents;
        if (mWheelType == WHEEL_TYPE.INMOTION)
        {
            InMotionAdapter.getInstance().setBetterPercents(betterPercents);
        }

    }

    public void setUseStopMusic(boolean useStopMusic)
    {
        mUseStopMusic = useStopMusic;
    }

    public void setVoltageSpeedThreshold(int voltageSpeedThreshold)
    {
        mVoltageSpeedThreshold = voltageSpeedThreshold;
    }

    public void setTiltBackVoltage(int tiltBackVoltage)
    {
        mTiltBackVoltage = tiltBackVoltage / 10.0;
    }

    public double getDistanceDouble()
    {
        return (mTotalDistance - mStartTotalDistance) / 1000.0;
    }

    double getTotalDistanceDouble()
    {
        return mTotalDistance / 1000.0;
    }

    long getTotalDistance()
    {
        return mTotalDistance;
    }

    ArrayList<String> getXAxis()
    {
        return xAxis;
    }

    ArrayList<Float> getCurrentAxis()
    {
        return currentAxis;
    }

    ArrayList<Float> getSpeedAxis()
    {
        return speedAxis;
    }

    void setAlarmsEnabled(boolean enabled)
    {
        mAlarmsEnabled = enabled;
    }

    void setUseRatio(boolean enabled)
    {
        mUseRatio = enabled;
    }

    void set18Lkm(boolean enabled)
    {
        m18Lkm = enabled;
        if ((mModel.compareTo("KS-18L") == 0) && !m18Lkm)
        {
            mTotalDistance = Math.round(mTotalDistance * KS18L_SCALER);
        }
    }

    void setGotwayVoltage(int voltage)
    {
        mGotwayVoltageScaler = voltage;
    }

    void setGotwayNegative(int negative)
    {
        mGotwayNegative = negative;
    }

    void setPreferences(int alarm1Speed, int alarm1Battery,
                        int alarm2Speed, int alarm2Battery,
                        int alarm3Speed, int alarm3Battery,
                        int alarmCurrent, int alarmTemperature,
                        boolean disablePhoneVibrate, boolean disablePhoneBeep,
                        boolean alteredAlarms, int rotationSpeed, int rotationVoltage,
                        int firstPwm, int secondPwm, int thirdPwm, int alarmFactor3, int warningSpeed,
                        int speedCorrection, int batteryCapacity, int chargingPower, int startRecommendPwm, int finishRecommendPwm)
    {
        mAlarm1Speed = alarm1Speed * 100;
        mAlarm2Speed = alarm2Speed * 100;
        mAlarm3Speed = alarm3Speed * 100;
        mAlarm1Battery = alarm1Battery;
        mAlarm2Battery = alarm2Battery;
        mAlarm3Battery = alarm3Battery;
        mAlarmCurrent = alarmCurrent * 100;
        mAlarmTemperature = alarmTemperature * 100;
        mDisablePhoneVibrate = disablePhoneVibrate;
        mDisablePhoneBeep = disablePhoneBeep;
        mAlteredAlarms = alteredAlarms;
        mRotationSpeed = (float) rotationSpeed / 10.0;
        mRotationVoltage = (float) rotationVoltage / 10.0;
        mFirstPwm = (float) firstPwm / 100.0;
        mSecondPwm = (float) secondPwm / 100.0;
        mThirdPwm = (float) thirdPwm / 100.0;
        mAlarmFactor3 = (float) alarmFactor3 / 100.0;
        mAdvanceWarningSpeed = warningSpeed;
        mSpeedCorrectionFactor = (float) speedCorrection / 1000.0;
        mBatteryCapacity = batteryCapacity;
        mChargingPowerAmp = (float) chargingPower / 10.0;
        mStartRecommendPwm = (float) startRecommendPwm / 100.0;
        mFinishRecommendPwm = (float) finishRecommendPwm / 100.0;
    }

    private int byteArrayInt2(byte low, byte high)
    {
        return (low & 255) + ((high & 255) * 256);
    }

    private long byteArrayInt4(byte value1, byte value2, byte value3, byte value4)
    {
        return (((((long) ((value1 & 255) << 16))) | ((long) ((value2 & 255) << 24))) | ((long) (value3 & 255))) | ((long) ((value4 & 255) << 8));
    }

    private void setCurrentTime(int currentTime)
    {
        if (mRideTime > (currentTime + TIME_BUFFER))
        {
            mLastRideTime += mRideTime;
        }
        mRideTime = currentTime;
    }

    private void setVoltageSag(int voltSag)
    {
        if ((voltSag < mVoltageSag) && (voltSag > 0))
        {
            mVoltageSag = voltSag;
        }
    }

    private void setBatteryPercent(int battery)
    {
        mBattery = battery;
        mAverageBattery = battery;
    }

    private void startSpeedAlarmCount()
    {
        mSpeedAlarmExecuting = true;
        TimerTask stopSpeedAlarmExecuring = new TimerTask()
        {
            @Override
            public void run()
            {
                mSpeedAlarmExecuting = false;
                Timber.i("Stop Speed <<<<<<<<<");
            }
        };

        Timer timerCurrent = new Timer();
        timerCurrent.schedule(stopSpeedAlarmExecuring, 170);

    }

    private void startTempAlarmCount()
    {
        mTemperatureAlarmExecuting = true;
        TimerTask stopTempAlarmExecuting = new TimerTask()
        {
            @Override
            public void run()
            {
                mTemperatureAlarmExecuting = false;
                Timber.i("Stop Temp <<<<<<<<<");
            }
        };

        Timer timerTemp = new Timer();
        timerTemp.schedule(stopTempAlarmExecuting, 570);
    }

    private void startCurrentAlarmCount()
    {
        mCurrentAlarmExecuting = true;
        TimerTask stopCurrentAlarmExecuring = new TimerTask()
        {
            @Override
            public void run()
            {
                mCurrentAlarmExecuting = false;
                Timber.i("Stop Curr <<<<<<<<<");
            }

        };

        Timer timerCurrent = new Timer();
        timerCurrent.schedule(stopCurrentAlarmExecuring, 170);
    }

    private void playWarningSpeed(Context mContext)
    {
        MediaPlayer mp1 = MediaPlayer.create(mContext, R.raw.sound_warning_speed);
        mp1.start();
        mp1.setOnCompletionListener(mp11 -> mp11.release());
    }

    private void playRecommendSpeed(Context mContext)
    {
        MediaPlayer mp1 = MediaPlayer.create(mContext, R.raw.exact);
        mp1.start();
        mp1.setOnCompletionListener(mp11 -> mp11.release());
    }

    private double getCorrectedSpeed()
    {
        return (float) mSpeed * mSpeedCorrectionFactor;
    }

    private void checkAlarmStatus(Context mContext)
    {
        if (mAlteredAlarms)
        {
            int safeLoadPercent = getSafeLoadPercent();
            if (safeLoadPercent > mMaxLoadPercent)
            {
                mMaxLoadPercent = safeLoadPercent;
            }
        }

        if (!mSpeedAlarmExecuting)
        {
            if (mAlteredAlarms)
            {
                // Default correction factor
                double maxCurrentNoLoadSpeed = mRotationSpeed / mRotationVoltage * mVoltage;
                double maxCurrentSafeSpeedForFirstPwm = maxCurrentNoLoadSpeed * mFirstPwm;
                double maxCurrentSafeSpeedForSecondPwm = maxCurrentNoLoadSpeed * mSecondPwm;
                double maxCurrentSafeSpeedForThirdPwm = maxCurrentNoLoadSpeed * mThirdPwm;

                // Check speed
                if (mStartRecommendPwm != 0 && mFinishRecommendPwm != 0 &&
                    getCorrectedSpeed() >= (maxCurrentNoLoadSpeed * mStartRecommendPwm) && getCorrectedSpeed() <= (maxCurrentNoLoadSpeed * mFinishRecommendPwm))
                {
                    if ((System.currentTimeMillis() - mLastPlayRecommendSpeedTime) > 3000)
                    {
                        mLastPlayRecommendSpeedTime = System.currentTimeMillis();
                        playRecommendSpeed(mContext);
                    }
                }
                else if (getCorrectedSpeed() > maxCurrentSafeSpeedForThirdPwm)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED3, mContext);
                }
                else if (getCorrectedSpeed() > maxCurrentSafeSpeedForSecondPwm)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED2, mContext);
                }
                else if (getCorrectedSpeed() > maxCurrentSafeSpeedForFirstPwm)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED1, mContext);
                }
                else if (mAdvanceWarningSpeed != 0 && getSpeedDouble() >= mAdvanceWarningSpeed && (System.currentTimeMillis() - mLastPlayWarningSpeedTime) > 3000)
                {
                    mLastPlayWarningSpeedTime = System.currentTimeMillis();
                    playWarningSpeed(mContext);
                }
            }
            else
            {
                if (mAlarm1Speed > 0 && mAlarm1Battery > 0 && mAverageBattery <= mAlarm1Battery && getCorrectedSpeed() >= mAlarm1Speed)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED1, mContext);
                }
                else if (mAlarm2Speed > 0 && mAlarm2Battery > 0 && mAverageBattery <= mAlarm2Battery && getCorrectedSpeed() >= mAlarm2Speed)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED2, mContext);
                }
                else if (mAlarm3Speed > 0 && mAlarm3Battery > 0 && mAverageBattery <= mAlarm3Battery && getCorrectedSpeed() >= mAlarm3Speed)
                {
                    startSpeedAlarmCount();
                    raiseAlarm(ALARM_TYPE.SPEED3, mContext);
                }
            }
        }

        if (mAlarmCurrent > 0 && mCurrent >= mAlarmCurrent && !mCurrentAlarmExecuting)
        {
            startCurrentAlarmCount();
            raiseAlarm(ALARM_TYPE.CURRENT, mContext);
        }

        if (mAlarmTemperature > 0 && mTemperature >= mAlarmTemperature && !mTemperatureAlarmExecuting)
        {
            startTempAlarmCount();
            raiseAlarm(ALARM_TYPE.TEMPERATURE, mContext);
        }
    }

    private void raiseAlarm(ALARM_TYPE alarmType, Context mContext)
    {
        Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        long[] pattern = {0};
        Intent intent = new Intent(Constants.ACTION_ALARM_TRIGGERED);
        intent.putExtra(Constants.INTENT_EXTRA_ALARM_TYPE, alarmType);

        switch (alarmType)
        {
            case SPEED1:
            case SPEED3:
            case SPEED2:
                pattern = new long[]{0, 100, 100};
                break;

            case CURRENT:
                pattern = new long[]{0, 50, 50, 50, 50};
                break;
            case TEMPERATURE:
                pattern = new long[]{0, 500, 500};
                break;
        }

        mContext.sendBroadcast(intent);
        if (v.hasVibrator() && !mDisablePhoneVibrate)
        {
            v.vibrate(pattern, -1);
        }

        if (!mDisablePhoneBeep)
        {
            playBeep(alarmType);
        }
    }

    void decodeResponse(byte[] data, Context mContext)
    {
        timestamp_raw = System.currentTimeMillis();
        StringBuilder stringBuilder = new StringBuilder(data.length);
        for (byte aData : data)
            stringBuilder.append(String.format(Locale.US, "%02X", aData));

        Timber.i("Received: " + stringBuilder.toString());
        Timber.i("Decode, proto: %s", protoVer);
        boolean new_data = false;
        if (mWheelType == WHEEL_TYPE.KINGSONG)
        {
            new_data = decodeKingSong(data);
        }
        else if (mWheelType == WHEEL_TYPE.GOTWAY)
        {
            new_data = decodeGotway(data);
        }
        else if (mWheelType == WHEEL_TYPE.INMOTION)
        {
            new_data = decodeInmotion(data);
        }
        else if (mWheelType == WHEEL_TYPE.NINEBOT || protoVer.compareTo("S2") == 0 || protoVer.compareTo("Mini") == 0)
        {
            Timber.i("Ninebot_decoding");
            new_data = decodeNinebot(data);
        }
        else if (mWheelType == WHEEL_TYPE.NINEBOT_Z)
        {
            Timber.i("Ninebot_z decoding");
            new_data = decodeNinebotZ(data);
        }

        if (!new_data)
        {
            return;
        }

        Intent intent = new Intent(Constants.ACTION_WHEEL_DATA_AVAILABLE);
        if (mNewWheelSettings)
        {
            intent.putExtra(Constants.INTENT_EXTRA_WHEEL_SETTINGS, true);
            mNewWheelSettings = false;
        }

        if (graph_last_update_time + GRAPH_UPDATE_INTERVAL < Calendar.getInstance().getTimeInMillis())
        {
            graph_last_update_time = Calendar.getInstance().getTimeInMillis();
            intent.putExtra(Constants.INTENT_EXTRA_GRAPH_UPDATE_AVILABLE, true);
            currentAxis.add((float) getCurrentDouble());
            speedAxis.add((float) getSpeedDouble());
            xAxis.add(new SimpleDateFormat("HH:mm:ss", Locale.US).format(Calendar.getInstance().getTime()));
            if (speedAxis.size() > (3600000 / GRAPH_UPDATE_INTERVAL))
            {
                speedAxis.remove(0);
                currentAxis.remove(0);
                xAxis.remove(0);
            }
        }

        if (mAlarmsEnabled)
        {
            checkAlarmStatus(mContext);
        }

        timestamp_last = timestamp_raw;
        mContext.sendBroadcast(intent);

        double speed = getSpeedDouble();
        if (speed == 0)
        {
            mLastRestBattery = mBattery;
            mLastRestVoltage = getVoltageDouble();
        }

        if (speed < mVoltageSpeedThreshold)
            mIsVoltageSpeedCalculating = false;

        // Voltage threshold calculation
        if (!mIsVoltageSpeedCalculating &&
             mVoltageSpeedThreshold != 0 &&
             mLastRestVoltage > 0 &&
             speed >= mVoltageSpeedThreshold &&
             (LoggingService.mLocation != null && ((LoggingService.mLocation.getSpeed() * 3.6) * 2) >= mVoltageSpeedThreshold))
        {
            mIsVoltageSpeedCalculating = true;
            double currentVoltageThreshold = mLastRestVoltage - getVoltageDouble();
            if (mVoltageThreshold == 0 || currentVoltageThreshold < mVoltageThreshold)
                mVoltageThreshold = currentVoltageThreshold;
        }

        calculateLeftMileage();
        if (mUseStopMusic)
        {
            if (speed <= 3.5)
            {
                mLowSpeedMusicTime = 0;
                MainActivity.audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            }
            else
            {
                if (mLowSpeedMusicTime == 0)
                {
                    mLowSpeedMusicTime = System.currentTimeMillis();
                }

                if ((System.currentTimeMillis() - mLowSpeedMusicTime) >= 1500)
                {
                    MainActivity.audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
                }
            }
        }
    }

    private double mStartBatteryDistance = 0;
    private double mStartBattery = 0;
    private void calculateLeftMileage()
    {
        if ((getWheelDistance() >= 1000 || getDistance() >= 1000) && getSpeed() == 0 && mStartBattery == 0)
        {
            mStartBatteryDistance = getDistanceDouble();
            mStartBattery = mBattery;
        }

        if (mStartBattery == 0 || getDistance() <= 500)
        {
            return;
        }

        double usedBattery = mStartBattery - mLastRestBattery;
        if (usedBattery <= 0)
        {
            return;
        }

        double possibleCount = mLastRestBattery / usedBattery;
        double currentDistance = getDistanceDouble() - mStartBatteryDistance;
        mLeftKm = possibleCount * currentDistance;
    }

    private boolean decodeKingSong(byte[] data)
    {
        if (rideStartTime == 0)
        {
            rideStartTime = Calendar.getInstance().getTimeInMillis();
            mRidingTime = 0;
        }
        if (data.length >= 20)
        {
            int a1 = data[0] & 255;
            int a2 = data[1] & 255;
            if (a1 != 170 || a2 != 85)
            {
                return false;
            }
            if ((data[16] & 255) == 169)
            {
                mVoltage = byteArrayInt2(data[2], data[3]);
                mSpeed = byteArrayInt2(data[4], data[5]);
                mTotalDistance = byteArrayInt4(data[6], data[7], data[8], data[9]);
                if ((mModel.compareTo("KS-18L") == 0) && !m18Lkm)
                {
                    mTotalDistance = Math.round(mTotalDistance * KS18L_SCALER);
                }

                mCurrent = ((data[10] & 0xFF) + (data[11] << 8));
                mTemperature = byteArrayInt2(data[12], data[13]);
                setVoltageSag(mVoltage);
                if ((data[15] & 255) == 224)
                {
                    mMode = data[14];
                    mModeStr = String.format(Locale.US, "%d", mMode);
                }

                int battery;
                if ((mModel.compareTo("KS-18L") == 0) || (mModel.compareTo("KS-16X") == 0) || (mBtName.compareTo("RW") == 0) || (mModel.compareTo("KS-18LH") == 0) || (mName.startsWith("ROCKW")))
                {
                    if (mBetterPercents)
                    {
                        if (mVoltage > 8350)
                        {
                            battery = 100;
                        }
                        else if (mVoltage > 6800)
                        {
                            battery = (mVoltage - 6650) / 17;
                        }
                        else if (mVoltage > 6400)
                        {
                            battery = (mVoltage - 6400) / 45;
                        }
                        else
                        {
                            battery = 0;
                        }
                    }
                    else
                    {
                        if (mVoltage < 6250)
                        {
                            battery = 0;
                        }
                        else if (mVoltage >= 8250)
                        {
                            battery = 100;
                        }
                        else
                        {
                            battery = (mVoltage - 6250) / 20;
                        }
                    }
                }
                else
                {
                    if (mBetterPercents)
                    {
                        if (mVoltage > 6680)
                        {
                            battery = 100;
                        }
                        else if (mVoltage > 5440)
                        {
                            battery = (int) Math.round((mVoltage - 5320) / 13.6);
                        }
                        else if (mVoltage > 5120)
                        {
                            battery = (mVoltage - 5120) / 36;
                        }
                        else
                        {
                            battery = 0;
                        }
                    }
                    else
                    {
                        if (mVoltage < 5000)
                        {
                            battery = 0;
                        }
                        else if (mVoltage >= 6600)
                        {
                            battery = 100;
                        }
                        else
                        {
                            battery = (mVoltage - 5000) / 16;
                        }
                    }

                }

                setBatteryPercent(battery);
                return true;
            }
            else if ((data[16] & 255) == 185)
            {
                // Distance/Time/Fan Data
                long distance = byteArrayInt4(data[2], data[3], data[4], data[5]);
                setDistance(distance);

                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
                setTopSpeed(byteArrayInt2(data[8], data[9]));
                mFanStatus = data[12];
            }
            else if ((data[16] & 255) == 187)
            {
                // Name and Type data
                int end = 0;
                int i = 0;
                while (i < 14 && data[i + 2] != 0)
                {
                    end++;
                    i++;
                }

                mName = new String(data, 2, end).trim();
                mModel = "";
                String[] ss = mName.split("-");
                for (i = 0; i < ss.length - 1; i++)
                {
                    if (i != 0)
                    {
                        mModel += "-";
                    }
                    mModel += ss[i];
                }
                try
                {
                    mVersion = String.format(Locale.US, "%.2f", Integer.parseInt(ss[ss.length - 1]) / 100.0);
                }
                catch (Exception ignored)
                {
                }
            }
            else if ((data[16] & 255) == 179)
            {
                // Serial Number
                byte[] sndata = new byte[18];
                System.arraycopy(data, 2, sndata, 0, 14);
                System.arraycopy(data, 17, sndata, 14, 3);
                sndata[17] = (byte) 0;
                mSerialNumber = new String(sndata);
                updateKSAlarmAndSpeed();
            }
            else if ((data[16] & 255) == 164 || (data[16] & 255) == 181)
            {
                //0xa4 || 0xb5 max speed and alerts
                mWheelMaxSpeed = (data[10] & 255);
                mKSAlarm3Speed = (data[8] & 255);
                mKSAlarm2Speed = (data[6] & 255);
                mKSAlarm1Speed = (data[4] & 255);
                mKSAlertsAndSpeedupdated = true;

                // after received 0xa4 send same repeat data[2] =0x01 data[16] = 0x98
                if ((data[16] & 255) == 164)
                {
                    data[16] = (byte) 0x98;
                    mBluetoothLeService.writeBluetoothGattCharacteristic(data);
                }

            }
        }
        return false;
    }

    private boolean decodeGotway(byte[] data)
    {
        Timber.i("Decode GOTWAY");
        if (rideStartTime == 0)
        {
            rideStartTime = Calendar.getInstance().getTimeInMillis();
            mRidingTime = 0;
        }

        if (data.length >= 20)
        {
            Timber.i("Len >=20");
            int a1 = data[0] & 255;
            int a2 = data[1] & 255;
            int a3 = data[2] & 255;
            int a4 = data[3] & 255;
            int a5 = data[4] & 255;
            int a6 = data[5] & 255;
            int a19 = data[18] & 255;
            if ((a1 == 0xDC) && (a2 == 0x5A) && (a3 == 0x5C) && (a4 == 0x20))
            {
                // Sherman
                Timber.i("Decode Sherman");
                mVeteran = true;
                mVoltage = (data[4] & 0xFF) << 8 | (data[5] & 0xFF);
                int speed = ((data[6]) << 8 | (data[7] & 0xFF))*10;
                if ((speed / 100) > 150 || (speed / 100) < -150)
                {
                    return false;
                }

                mSpeed =  speed;
                long distance = ((data[10] & 0xFF) << 24 | (data[11] & 0xFF) << 16 | (data[8] & 0xFF) << 8 | (data[9] & 0xFF));
                setDistance(distance);
                mTotalDistance = ((data[14] & 0xFF) << 24 | (data[15] & 0xFF) << 16 | (data[12] & 0xFF) << 8 | (data[13] & 0xFF));
                mCurrent = ((data[16]) << 8 | (data[17] & 0xFF))*10;
                mTemperature = (data[18] & 0xFF) << 8 | (data[19] & 0xFF);
                mTemperature2 = mTemperature;
                setTopSpeed(getCorrectedSpeed());

                int battery;
                if (mBetterPercents)
                {
                    battery = 0;
                    double currentVoltage = (double) mVoltage / 100.0;
                    mostMinVoltage = 75.6 + mVoltageThreshold;
                    mostPercentStep = (100.8 - mostMinVoltage) / 100;
                    battery = (int) Math.round((currentVoltage - mostMinVoltage) / mostPercentStep);
                }
                else
                {
                    if (mVoltage <= 7935)
                    {
                        battery = 0;
                    }
                    else if (mVoltage >= 9870)
                    {
                        battery = 100;
                    }
                    else
                    {
                        battery = (int) Math.round((mVoltage - 7935) / 19.5);
                    }
                }

                setBatteryPercent(battery);
                setVoltageSag(mVoltage);
                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
                return true;
            }
            else
            {
                // Gotway
                if (a1 != 85 || a2 != 170 || a19 != 0)
                {
                    if (a1 != 90 || a5 != 85 || a6 != 170)
                    {
                        return false;
                    }

                    mTotalDistance = ((data[6] & 0xFF) << 24) | ((data[7] & 0xFF) << 16) | ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
                    if (mUseRatio) mTotalDistance = Math.round(mTotalDistance * RATIO_GW);
                    return false;
                }

                if (data[5] >= 0)
                {
                    if (mGotwayNegative == 0)
                    {
                        int speed = (int) Math.abs(((data[4] * 256.0) + data[5]) * 3.6);
                        if ((speed / 100) > 150 || (speed / 100) < -150)
                        {
                            return false;
                        }

                        mSpeed = speed;
                    }
                    else
                    {
                        int speed = ((int) (((data[4] * 256.0) + data[5]) * 3.6)) * mGotwayNegative;
                        if ((speed / 100) > 150 || (speed / 100) < -150)
                        {
                            return false;
                        }

                        mSpeed = speed;
                    }
                }
                else if (mGotwayNegative == 0)
                {
                    int speed = (int) Math.abs((((data[4] * 256.0) + 256.0) + data[5]) * 3.6);
                    if ((speed / 100) > 150 || (speed / 100) < -150)
                    {
                        return false;
                    }

                    mSpeed = speed;
                }
                else
                {
                    int speed = ((int) ((((data[4] * 256.0) + 256.0) + data[5]) * 3.6)) * mGotwayNegative;
                    if ((speed / 100) > 150 || (speed / 100) < -150)
                    {
                        return false;
                    }

                    mSpeed = speed;
                }

                if (mUseRatio) mSpeed = (int) Math.round(getCorrectedSpeed() * RATIO_GW);
                setTopSpeed(getCorrectedSpeed());

                mTemperature = (int) Math.round(((((data[12] * 256) + data[13]) / 340.0) + 35) * 100);
                mTemperature2 = mTemperature;

                long distance = byteArrayInt2(data[9], data[8]);
                if (mUseRatio) distance = Math.round(distance * RATIO_GW);
                setDistance(distance);

                mVoltage = (data[2] * 256) + (data[3] & 255);

                mCurrent = ((data[10] * 256) + data[11]);
                if (mGotwayNegative == 0) mCurrent = Math.abs(mCurrent);
                else mCurrent = mCurrent * mGotwayNegative;

                int battery;
                if (mBetterPercents)
                {
                    battery = 0;
                    double currentVoltage = ((double) mVoltage * (1.0 + (0.25 * (double) mGotwayVoltageScaler))) / 100.0;
                    switch (mGotwayVoltageScaler)
                    {
                        case 0:
                            mostMinVoltage = mTiltBackVoltage + mVoltageThreshold;
                            mostPercentStep = (67.2 - mostMinVoltage) / 100;
                            battery = (int) Math.round((currentVoltage - mostMinVoltage) / mostPercentStep);
                            break;
                        case 1:
                            mostMinVoltage = mTiltBackVoltage + mVoltageThreshold;
                            mostPercentStep = (84.0 - mostMinVoltage) / 100;
                            battery = (int) Math.round((currentVoltage - mostMinVoltage) / mostPercentStep);
                            break;
                        case 2:
                            mostMinVoltage = mTiltBackVoltage + mVoltageThreshold;
                            mostPercentStep = (100.8 - mostMinVoltage) / 100;
                            battery = (int) Math.round((currentVoltage - mostMinVoltage) / mostPercentStep);
                            break;
                    }
                }
                else
                {
                    if (mVoltage <= 5290)
                    {
                        battery = 0;
                    }
                    else if (mVoltage >= 6580)
                    {
                        battery = 100;
                    }
                    else
                    {
                        battery = (mVoltage - 5290) / 13;
                    }
                }

                setBatteryPercent(battery);
                mVoltage = (int)Math.round(mVoltage * (1 + (0.25 * mGotwayVoltageScaler)));
                setVoltageSag(mVoltage);
                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
            }

            return true;
        }
        else if (data.length >= 10 && !mVeteran)
        {
            int a1 = data[0];
            int a5 = data[4] & 255;
            int a6 = data[5] & 255;
            if (a1 != 90 || a5 != 85 || a6 != 170)
            {
                return false;
            }

            mTotalDistance = ((data[6]&0xFF) <<24) | ((data[7]&0xFF) << 16) | ((data[8] & 0xFF) <<8) | (data[9] & 0xFF);
            if (mUseRatio) mTotalDistance = Math.round(mTotalDistance * RATIO_GW);
        }

        return false;
    }

    double mostMinVoltage, mostPercentStep;
    private boolean decodeNinebotZ(byte[] data)
    {
        ArrayList<NinebotZAdapter.Status> statuses = NinebotZAdapter.getInstance().charUpdated(data);
        if (statuses.size() < 1) return false;
        if (rideStartTime == 0)
        {
            rideStartTime = Calendar.getInstance().getTimeInMillis();
            mRidingTime = 0;
        }

        for (NinebotZAdapter.Status status : statuses)
        {
            Timber.i(status.toString());
            if (status instanceof NinebotZAdapter.serialNumberStatus)
            {
                mSerialNumber = ((NinebotZAdapter.serialNumberStatus) status).getSerialNumber();
                mModel = "Ninebot Z";
            }
            else if (status instanceof NinebotZAdapter.versionStatus)
            {
                mVersion = ((NinebotZAdapter.versionStatus) status).getVersion();
            }
            else
            {
                mSpeed = status.getSpeed();
                mVoltage = status.getVoltage();
                mBattery = status.getBatt();
                mCurrent = status.getCurrent();
                mTotalDistance = status.getDistance();
                mTemperature = status.getTemperature() * 10;

                setDistance(status.getDistance());
                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
                setBatteryPercent(mBattery);
                setTopSpeed(getCorrectedSpeed());
                setVoltageSag(mVoltage);
            }
        }

        return true;
    }

    private boolean decodeNinebot(byte[] data)
    {
        ArrayList<NinebotAdapter.Status> statuses = NinebotAdapter.getInstance().charUpdated(data);
        if (statuses.size() < 1) return false;
        if (rideStartTime == 0)
        {
            rideStartTime = Calendar.getInstance().getTimeInMillis();
            mRidingTime = 0;
        }

        for (NinebotAdapter.Status status : statuses)
        {
            Timber.i(status.toString());
            if (status instanceof NinebotAdapter.serialNumberStatus)
            {
                mSerialNumber = ((NinebotAdapter.serialNumberStatus) status).getSerialNumber();
                mModel = "Ninebot" + " " + protoVer;
            }
            else if (status instanceof NinebotAdapter.versionStatus)
            {
                mVersion = ((NinebotAdapter.versionStatus) status).getVersion();
            }
            else
            {
                mSpeed = status.getSpeed();
                mVoltage = status.getVoltage();
                mBattery = status.getBatt();
                mCurrent = status.getCurrent();
                mTotalDistance = status.getDistance();
                mTemperature = status.getTemperature() * 10;

                setDistance(status.getDistance());
                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
                setTopSpeed(getCorrectedSpeed());
                setVoltageSag(mVoltage);
                setBatteryPercent(mBattery);
            }
        }

        return true;
    }

    private boolean decodeInmotion(byte[] data)
    {
        ArrayList<InMotionAdapter.Status> statuses = InMotionAdapter.getInstance().charUpdated(data);
        if (statuses.size() < 1) return false;
        if (rideStartTime == 0)
        {
            rideStartTime = Calendar.getInstance().getTimeInMillis();
            mRidingTime = 0;
        }

        for (InMotionAdapter.Status status : statuses)
        {
            Timber.i(status.toString());
            if (status instanceof InMotionAdapter.Infos)
            {
                mWheelLightEnabled = ((InMotionAdapter.Infos) status).getLightState();
                mWheelLedEnabled = ((InMotionAdapter.Infos) status).getLedState();
                mWheelButtonDisabled = ((InMotionAdapter.Infos) status).getHandleButtonState();
                mWheelMaxSpeed = ((InMotionAdapter.Infos) status).getMaxSpeedState();
                mWheelSpeakerVolume = ((InMotionAdapter.Infos) status).getSpeakerVolumeState();
                mWheelTiltHorizon = ((InMotionAdapter.Infos) status).getTiltHorizon();
                mSerialNumber = ((InMotionAdapter.Infos) status).getSerialNumber();
                mModel = ((InMotionAdapter.Infos) status).getModelString();
                mVersion = ((InMotionAdapter.Infos) status).getVersion();
                mNewWheelSettings = true;
            }
            else if (status instanceof InMotionAdapter.Alert)
            {
                if (mAlert == "")
                {
                    mAlert = ((InMotionAdapter.Alert) status).getfullText();
                }
                else
                {
                    mAlert = mAlert + " | " + ((InMotionAdapter.Alert) status).getfullText();
                }
            }
            else
            {
                mSpeed = (int) (status.getSpeed() * 360d);
                mVoltage = (int) (status.getVoltage() * 100d);
                mCurrent = (int) (status.getCurrent() * 100d);
                mTemperature = (int) (status.getTemperature() * 100d);
                mTemperature2 = (int) (status.getTemperature2() * 100d);
                mTotalDistance = (long) (status.getDistance() * 1000d);
                mAngle = status.getAngle();
                mRoll = status.getRoll();

                mModeStr = status.getWorkModeString();
                setBatteryPercent((int) status.getBatt());
                setDistance((long) status.getDistance());

                int currentTime = (int) (Calendar.getInstance().getTimeInMillis() - rideStartTime) / 1000;
                setCurrentTime(currentTime);
                setTopSpeed(getCorrectedSpeed());
                setVoltageSag(mVoltage);
            }
        }

        return true;
    }

    void full_reset()
    {
        if (mWheelType == WHEEL_TYPE.INMOTION) InMotionAdapter.getInstance().stopTimer();
        if (mWheelType == WHEEL_TYPE.NINEBOT_Z)
        {
            if (protoVer.compareTo("S2") == 0)
            {
                Timber.i("Ninebot S2 stop!");
                NinebotAdapter.getInstance().stopTimer();
            }
            else if (protoVer.compareTo("Mini") == 0)
            {
                Timber.i("Ninebot Mini stop!");
                NinebotAdapter.getInstance().stopTimer();
            }
            else
            {
                Timber.i("Ninebot Z stop!");
                NinebotZAdapter.getInstance().stopTimer();
            }
        }

        if (mWheelType == WHEEL_TYPE.NINEBOT) NinebotAdapter.getInstance().stopTimer();
        mBluetoothLeService = null;
        mWheelType = WHEEL_TYPE.Unknown;
        xAxis.clear();
        speedAxis.clear();
        currentAxis.clear();
        reset();
    }

    void reset()
    {
        mAvgSafeLoadPercentRes = 0;
        mAvgSafeLoadPercentResCount = 0;
        mLowSpeedMusicTime = 0;
        mLastRestBattery = 0;
        mLastRestVoltage = 0;
        mStartVoltage = 0;
        mStartVoltageDistance = 0;
        mVoltageSpeedThreshold = 0;
        mVoltageThreshold = 0;
        mostMinVoltage = 0;
        mostPercentStep = 0;
        mIsVoltageSpeedCalculating = false;
        mUserLog.clear();
        mLeftKm = 0;
        mMaxLoadPercent = 0;
        mSpeed = 0;
        mTotalDistance = 0;
        mCurrent = 0;
        mTemperature = 0;
        mTemperature2 = 0;
        mAngle = 0;
        mRoll = 0;
        mMode = 0;
        mBattery = 0;
        mAverageBattery = 0;
        mVoltage = 0;
        mVoltageSag = 20000;
        mRideTime = 0;
        mRidingTime = 0;
        mTopSpeed = 0;
        mFanStatus = 0;
        mDistance = 0;
        mUserDistance = 0;
        mName = "";
        mModel = "";
        mModeStr = "";
        mVersion = "";
        mSerialNumber = "";
        mBtName = "";
        rideStartTime = 0;
        mStartTotalDistance = 0;
        mWheelTiltHorizon = 0;
        mWheelLightEnabled = false;
        mWheelLedEnabled = false;
        mWheelButtonDisabled = false;
        mWheelMaxSpeed = 0;
        mWheelSpeakerVolume = 50;
        protoVer = "";
    }

    boolean detectWheel(BluetoothLeService bluetoothService, String deviceAddress)
    {
        mBluetoothLeService = bluetoothService;
        Context mContext = bluetoothService.getApplicationContext();
        String advData = SettingsUtil.getAdvDataForWheel(mContext, deviceAddress);

        protoVer = "";
        if (advData.compareTo("4e421300000000ec") == 0)
        {
            protoVer = "S2";
        }
        else if ((advData.compareTo("4e421400000000eb") == 0) || (advData.compareTo("4e422000000000df") == 0) ||
                (advData.compareTo("4e422200000000dd") == 0) || (advData.compareTo("4e4230cf") == 0)
                || advData.startsWith("5600"))
        {
            protoVer = "Mini";
        }

        Class<R.array> res = R.array.class;
        String[] wheel_types = mContext.getResources().getStringArray(R.array.wheel_types);
        for (String wheel_Type : wheel_types)
        {
            boolean detected_wheel = true;
            java.lang.reflect.Field services_res = null;
            try
            {
                services_res = res.getField(wheel_Type + "_services");
            }
            catch (Exception ignored)
            {
            }
            int services_res_id = 0;
            if (services_res != null)
            {
                try
                {
                    services_res_id = services_res.getInt(null);
                }
                catch (Exception ignored)
                {
                }
            }

            String[] services = mContext.getResources().getStringArray(services_res_id);
            if (services.length != mBluetoothLeService.getSupportedGattServices().size())
            {
                continue;
            }

            for (String service_uuid : services)
            {
                UUID s_uuid = UUID.fromString(service_uuid.replace("_", "-"));
                BluetoothGattService service = mBluetoothLeService.getGattService(s_uuid);
                if (service != null)
                {
                    java.lang.reflect.Field characteristic_res = null;
                    try
                    {
                        characteristic_res = res.getField(wheel_Type + "_" + service_uuid);
                    }
                    catch (Exception ignored)
                    {
                    }

                    int characteristic_res_id = 0;
                    if (characteristic_res != null)
                    {
                        try
                        {
                            characteristic_res_id = characteristic_res.getInt(null);
                        }
                        catch (Exception ignored)
                        {
                        }
                    }

                    String[] characteristics = mContext.getResources().getStringArray(characteristic_res_id);
                    for (String characteristic_uuid : characteristics)
                    {
                        UUID c_uuid = UUID.fromString(characteristic_uuid.replace("_", "-"));
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(c_uuid);
                        if (characteristic == null)
                        {
                            detected_wheel = false;
                            break;
                        }
                    }
                }
                else
                {
                    detected_wheel = false;
                    break;
                }
            }

            if (detected_wheel)
            {
                // Update preferences
                final Intent intent = new Intent(Constants.ACTION_WHEEL_TYPE_RECOGNIZED);
                intent.putExtra(Constants.INTENT_EXTRA_WHEEL_TYPE, wheel_Type);
                mContext.sendBroadcast(intent);
                Timber.i("Protocol recognized as %s", wheel_Type);

                if (mContext.getResources().getString(R.string.gotway).equals(wheel_Type) && (mBtName.equals("RW") || mName.startsWith("ROCKW")))
                {
                    Timber.i("It seems to be RochWheel, force to Kingsong proto");
                    wheel_Type = mContext.getResources().getString(R.string.kingsong);
                }

                if (mContext.getResources().getString(R.string.kingsong).equals(wheel_Type))
                {
                    mWheelType = WHEEL_TYPE.KINGSONG;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.KINGSONG_SERVICE_UUID));
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.KINGSONG_READ_CHARACTER_UUID));
                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(UUID.fromString(Constants.KINGSONG_DESCRIPTER_UUID));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothLeService.writeBluetoothGattDescriptor(descriptor);

                    return true;
                }
                else if (mContext.getResources().getString(R.string.gotway).equals(wheel_Type))
                {
                    mWheelType = WHEEL_TYPE.GOTWAY;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.GOTWAY_SERVICE_UUID));
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.GOTWAY_READ_CHARACTER_UUID));
                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);

                    // Let the user know it's working by making the wheel beep
                    mBluetoothLeService.writeBluetoothGattCharacteristic("b".getBytes());
                    return true;
                }
                else if (mContext.getResources().getString(R.string.inmotion).equals(wheel_Type))
                {
                    mWheelType = WHEEL_TYPE.INMOTION;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.INMOTION_SERVICE_UUID));
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.INMOTION_READ_CHARACTER_UUID));
                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(UUID.fromString(Constants.INMOTION_DESCRIPTER_UUID));
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    mBluetoothLeService.writeBluetoothGattDescriptor(descriptor);
                    if (SettingsUtil.hasPasswordForWheel(mContext, mBluetoothLeService.getBluetoothDeviceAddress()))
                    {
                        String inmotionPassword = SettingsUtil.getPasswordForWheel(mBluetoothLeService.getApplicationContext(), mBluetoothLeService.getBluetoothDeviceAddress());
                        InMotionAdapter.getInstance().startKeepAliveTimer(mBluetoothLeService, inmotionPassword);
                        return true;
                    }

                    return false;
                }
                else if (mContext.getResources().getString(R.string.ninebot_z).equals(wheel_Type))
                {
                    Timber.i("Trying to start Ninebot Z");
                    mWheelType = WHEEL_TYPE.NINEBOT_Z;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.NINEBOT_Z_SERVICE_UUID));
                    Timber.i("service UUID");
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.NINEBOT_Z_READ_CHARACTER_UUID));
                    Timber.i("read UUID");
                    if (notifyCharacteristic == null)
                    {
                        Timber.i("it seems that RX UUID doesn't exist");
                    }

                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    Timber.i("notify UUID");
                    BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(UUID.fromString(Constants.NINEBOT_Z_DESCRIPTER_UUID));
                    Timber.i("descr UUID");
                    if (descriptor == null)
                    {
                        Timber.i("it seems that descr UUID doesn't exist");
                    }

                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    Timber.i("enable notify UUID");
                    mBluetoothLeService.writeBluetoothGattDescriptor(descriptor);
                    Timber.i("write notify");
                    if (protoVer.compareTo("S2") == 0 || protoVer.compareTo("Mini") == 0)
                    {
                        NinebotAdapter.getInstance().startKeepAliveTimer(mBluetoothLeService, "", protoVer);
                    }
                    else
                    {
                        NinebotZAdapter.getInstance().startKeepAliveTimer(mBluetoothLeService, "");
                    }

                    Timber.i("starting ninebot adapter");
                    return true;
                }
                else if (mContext.getResources().getString(R.string.ninebot).equals(wheel_Type))
                {
                    Timber.i("Trying to start Ninebot");
                    mWheelType = WHEEL_TYPE.NINEBOT;
                    BluetoothGattService targetService = mBluetoothLeService.getGattService(UUID.fromString(Constants.NINEBOT_SERVICE_UUID));
                    Timber.i("service UUID");
                    BluetoothGattCharacteristic notifyCharacteristic = targetService.getCharacteristic(UUID.fromString(Constants.NINEBOT_READ_CHARACTER_UUID));
                    Timber.i("read UUID");
                    if (notifyCharacteristic == null)
                    {
                        Timber.i("it seems that RX UUID doesn't exist");
                    }

                    mBluetoothLeService.setCharacteristicNotification(notifyCharacteristic, true);
                    Timber.i("notify UUID");
                    BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(UUID.fromString(Constants.NINEBOT_DESCRIPTER_UUID));
                    Timber.i("descr UUID");
                    if (descriptor == null)
                    {
                        Timber.i("it seems that descr UUID doesn't exist");
                    }

                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    Timber.i("enable notify UUID");
                    mBluetoothLeService.writeBluetoothGattDescriptor(descriptor);
                    Timber.i("write notify");
                    NinebotAdapter.getInstance().startKeepAliveTimer(mBluetoothLeService, "", protoVer);
                    Timber.i("starting ninebot adapter");
                    return true;
                }
            }
        }

        return false;
    }
}