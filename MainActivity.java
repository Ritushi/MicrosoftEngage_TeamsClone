package com.ritushi.video_call;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import io.agora.rtc.Constants;
import io.agora.rtc.IRtcChannelEventHandler;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.models.UserInfo;
import io.agora.rtc.video.VideoCanvas;
import io.agora.rtc.video.VideoEncoderConfiguration;
//import io.agora.rtm.RtmClient;


import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSION_REQ_ID = 22;

    //Method that stores permission provided by user
    private static final String[] REQUESTED_PERMISSIONS ={
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private RtcEngine mRtcEngine;

    private RelativeLayout mRemoteContainer;
    private FrameLayout mLocalContainer;
    private SurfaceView mLocalView;
    private SurfaceView mRemoteView;

    private ImageView mCallBtn;
    private ImageView mMuteBtn;
    private ImageView mSwitchCameraBtn;

    private boolean mCallEnd;
    private  boolean mMuted;



    private final IRtcEngineEventHandler mRtcHandler = new IRtcEngineEventHandler() {

        @Override
        //When local user joins the channel
        public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
            super.onJoinChannelSuccess(channel, uid, elapsed);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("agora","Join channel success, uid: " + (uid & 0xFFFFFFFFL));
                }
            });
        }

        @Override
        //When remote user leaves the channel
        public void onUserOffline(final int uid, int reason) {
            super.onUserOffline(uid, reason);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("agora","User offline, uid: " + (uid & 0xFFFFFFFFL));
                    removeRemoteVideo();
                }
            });
        }

        @Override
        //When remote user joins channel
        public void onRemoteVideoStateChanged(final int uid, int state, int reason, int elapsed) {
            super.onRemoteVideoStateChanged(uid, state, reason, elapsed);
            if(state == Constants.REMOTE_VIDEO_STATE_STARTING){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.i("agora","Remote video starting, uid: " + (uid & 0xFFFFFFFFL));
                        setupRemoteVideo(uid);
                    }
                });
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        if (checkSelfPermission(REQUESTED_PERMISSIONS[0], PERMISSION_REQ_ID) &&
                checkSelfPermission(REQUESTED_PERMISSIONS[1], PERMISSION_REQ_ID)) {
            initEngineAndJoinChannel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(!mCallEnd){
            leaveChannel();
        }
        RtcEngine.destroy();
    }

    private void initUI(){
        mLocalContainer = findViewById(R.id.local_video_view_container);
        mRemoteContainer = findViewById((R.id.remote_video_view_container));

        mCallBtn = findViewById((R.id.btn_call));
        mMuteBtn = findViewById((R.id.btn_mute));
        mSwitchCameraBtn = findViewById((R.id.btn_switch_camera));
    }
    private void initEngineAndJoinChannel(){
        //initialize Engine
        initializeEngine();
        //setup video configuration
        setupVideoConfig();
        //setup local video
        setupLocalVideo();
        //join channel
        joinChannel();

    }

    private void initializeEngine() {
        try {
            mRtcEngine = RtcEngine.create(getBaseContext(), getString(R.string.App_ID), mRtcHandler);
        } catch (Exception e) {
            Log.e(TAG, Log.getStackTraceString(e));
            throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
        }
    }

    private void setupVideoConfig(){
        mRtcEngine.enableVideo();

        mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
        ));
    }

    private void setupLocalVideo(){
        mRtcEngine.enableVideo();

        mLocalView = RtcEngine.CreateRendererView(getBaseContext());
        mLocalView.setZOrderMediaOverlay(true);
        mLocalContainer.addView(mLocalView);

        VideoCanvas localVideoCanvas = new VideoCanvas(mLocalView, VideoCanvas.RENDER_MODE_HIDDEN, 0);
        mRtcEngine.setupLocalVideo(localVideoCanvas);
    }

    private void setupRemoteVideo(int uid) {
        int count = mRemoteContainer.getChildCount();
        View view = null;
        for(int i =0 ;i<count;i++){
            View v = mRemoteContainer.getChildAt(i);
            if(v.getTag() instanceof Integer && ((int) v.getTag()) == uid){
                view = v;
            }
        }

        if(view != null){
            return;
        }

        mRemoteView = RtcEngine.CreateRendererView(getBaseContext());
        mRemoteContainer.addView(mRemoteView);
        mRtcEngine.setupRemoteVideo(new VideoCanvas(mRemoteView, VideoCanvas.RENDER_MODE_HIDDEN, uid));
        //mRemoteView.setTag(uid);
    }

    private void removeRemoteVideo(){
        if(mRemoteView != null){
            mRemoteContainer.removeView(mRemoteView);
        }

        mRemoteView = null;
    }

    private void joinChannel(){
        String token = getString(R.string.Access_Token);
        if(TextUtils.isEmpty(token)){
            token = null;
        }

        mRtcEngine.joinChannel(token, "Channel1","",0);
    }

    private void leaveChannel(){
        mRtcEngine.leaveChannel();
    }
    public void onLocalAudioMuteClicked(View view){
        mMuted = !mMuted;
        mRtcEngine.muteLocalAudioStream(mMuted);
        int res = mMuted ? R.drawable.btn_unmute : R.drawable.btn_mute;
        mMuteBtn.setImageResource(res);
    }

    public void onSwitchCameraClicked(View view) {
        mRtcEngine.switchCamera();
    }

    public void  onCallClicked(View view){
        if(mCallEnd){
            startCall();
            mCallEnd = false;
            mCallBtn.setImageResource(R.drawable.btn_endcall);
        } else {
            endCall();
            mCallEnd = true;
            mCallBtn.setImageResource(R.drawable.btn_startcall);
        }

        showButtons(!mCallEnd);
    }

    private void startCall(){
        setupLocalVideo();
        joinChannel();
    }

    private void endCall(){
        removeLocalVideo();
        removeRemoteVideo();
        leaveChannel();
    }

    private void removeLocalVideo(){
        if(mLocalView!= null){
            mLocalContainer.removeView(mLocalView);
        }

        mLocalView = null;
    }

    private void showButtons(boolean show){
        int visibilty = show ? View.VISIBLE : View.GONE;
        mMuteBtn.setVisibility(visibilty);
        mSwitchCameraBtn.setVisibility(visibilty);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch(requestCode){
            case PERMISSION_REQ_ID : {
                if(grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED){
                    break;
                }
                initEngineAndJoinChannel();
                break;
            }
        }
    }

    private boolean checkSelfPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUESTED_PERMISSIONS, requestCode);
            return false;
        }

        return true;
    }
}