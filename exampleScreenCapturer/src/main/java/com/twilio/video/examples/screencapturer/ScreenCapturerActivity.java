package com.twilio.video.examples.screencapturer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.enterprise.feedback.KeyedAppState;
import androidx.enterprise.feedback.KeyedAppStatesCallback;
import androidx.enterprise.feedback.KeyedAppStatesReporter;

import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalAudioTrackPublication;
import com.twilio.video.LocalDataTrack;
import com.twilio.video.LocalDataTrackPublication;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.LocalVideoTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.Room;
import com.twilio.video.ScreenCapturer;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** This example demonstrates how to use the screen capturer */
public class ScreenCapturerActivity extends AppCompatActivity {
    private static final String TAG = "SCREEN_CAPTURE";
    private static final int REQUEST_MEDIA_PROJECTION = 100;

    private LocalParticipant localParticipant;
    private LocalDataTrack localDataTrack;
    private LocalVideoTrack screenVideoTrack;
    private ScreenCapturer screenCapturer;
    private MenuItem screenCaptureMenuItem;
    private ScreenCapturerManager screenCapturerManager;
    private Room room;

    private RequestQueue mRequestQueue;
    private StringRequest mStringRequest;
    private String url = "https://twilio-dot-app-launcher-dev-5ef0fa0a.uc.r.appspot.com/token";

    String tokenValue;
    String roomName;

    ProgressBar pgsBar;
    KeyedAppStatesReporter reporter;

    private Button mButton;
    private Switch sw;

    private final ScreenCapturer.Listener screenCapturerListener =
            new ScreenCapturer.Listener() {
                @Override
                public void onScreenCaptureError(@NonNull String errorDescription) {
                    Log.e(TAG, "Screen capturer error: " + errorDescription);
                    stopScreenCapture();
                    Toast.makeText(
                                    ScreenCapturerActivity.this,
                                    R.string.screen_capture_error,
                                    Toast.LENGTH_LONG)
                            .show();
                }

                @Override
                public void onFirstFrameAvailable() {
                    Log.d(TAG, "First frame from screen capturer available");
                }
            };

    @SuppressLint("WrongViewCast")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_capturer);
        reporter = KeyedAppStatesReporter.create(this);

        if (Build.VERSION.SDK_INT >= 29) {
            screenCapturerManager = new ScreenCapturerManager(this);
        }
        pgsBar = (ProgressBar)findViewById(R.id.pBar);
        pgsBar.setVisibility(View.GONE);
        mButton = findViewById(R.id.button_send);
        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                JSONObject jObjectData = new JSONObject();
                try {
                    jObjectData.put("action", "NEW");
                    jObjectData.put("type", "NOTHING");
                    jObjectData.put("data", null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                String jObjectDataString = jObjectData.toString();
                Log.i(TAG,  "Send DataTrack jObjectDataString " + jObjectDataString);
                sendMessageToDataTrack(jObjectDataString, false);
            }
        });

        sw = (Switch) findViewById(R.id.switch1);
        sw.setTextSize( 28 );
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    sw.setText( R.string.drivingOn );
                } else {
                    sw.setText( R.string.drivingOff );
                }
                Snackbar.make(buttonView, "Driving state changes to "+isChecked, Snackbar.LENGTH_SHORT)
                        .setAction("ACTION",null).show();
            }
        });

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Log.i(TAG, key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
            }
            String fcmType = getIntent().getStringExtra("firebaseCloudMessageType");
            roomName = getIntent().getStringExtra("roomId");

            Log.i(TAG,  " fcmType " + fcmType);
            Log.i(TAG,  " roomId " + roomName);

            if (Objects.equals(fcmType, "REMOTE_ACCESS_REQUEST") && roomName != null) {
                triggerScreenShare(fcmType, roomName);
            }
        }

        // Retrieving the value using its keys the file name must be same in both saving and retrieving the data
        SharedPreferences sh = getSharedPreferences("MySharedPref", Context.MODE_PRIVATE);
        // The value will be default as empty string because for the very
        // first time when the app is opened, there is nothing to show
        String s1 = sh.getString("fcmToken", "");
        sendRegistrationToServer(s1);
    }

    private void sendRegistrationToServer(String token) {
        final String[] message = {"null"};
        Collection states = new HashSet<KeyedAppState>();
        states.add(KeyedAppState.builder()
                .setKey("fcmToken")
                .setSeverity(KeyedAppState.SEVERITY_INFO)
                .setMessage("FCM token updated")
                .setData(token)
                .build());
        reporter.setStatesImmediate(states, new KeyedAppStatesCallback() {
            @Override
            public void onResult(int state, @Nullable Throwable throwable) {
                if (state == KeyedAppStatesCallback.STATUS_SUCCESS) {
                    message[0] = new String("Values sent to KAS");
                    Log.d(TAG, "Values sent to KAS");
                } else {
                    message[0] = "Failed to write to KAS";
                    Log.d(TAG, "Failed to write to KAS");
                }
            }
        });
        Toast.makeText(getApplicationContext(), message[0], Toast.LENGTH_LONG).show();//display the response on screen
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.screen_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Grab menu items for updating later
        screenCaptureMenuItem = menu.findItem(R.id.share_screen_menu_item);
        return true;
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share_screen_menu_item:
                String shareScreen = getString(R.string.share_screen);
                if (item.getTitle().equals(shareScreen)) {
                    try {
                        getData("LC03lkiust_R9BMC02257J", "TestUser");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= 29) {
                        screenCapturerManager.endForeground();
                    }
                    stopScreenCapture();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void handleScreenShareTrigger() {
        Log.d(TAG, "Handling screen share trigger");
        if(screenCapturerManager == null){
            Log.d(TAG, "handleScreenShareTrigger screenCapturerManager null");
        }
        if (Build.VERSION.SDK_INT >= 29) {
            Log.d(TAG, "handleScreenShareTrigger screenCapturerManager startForeground");
            screenCapturerManager.startForeground();
        }
        if (screenCapturer == null) {
            Log.d(TAG, "handleScreenShareTrigger screenCapturer null");
            requestScreenCapturePermission();
        } else {
            Log.d(TAG, "handleScreenShareTrigger startScreenCapture");
            startScreenCapture();
        }
    }

    private void requestScreenCapturePermission() {
        Log.d(TAG, "Requesting permission to capture screen");
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != AppCompatActivity.RESULT_OK) {
                Log.i(TAG,  " onActivityResult NOT Ok" + resultCode);
                handleDeclineEvent("DECLINE", "USER_CANCELS");
                Toast.makeText(
                                this,
                                R.string.screen_capture_permission_not_granted,
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }

            screenCapturer = new ScreenCapturer(this, resultCode, data, screenCapturerListener);
            startScreenCapture();
        }
    }

    private void handleDeclineEvent(String action, String type) {
        JSONObject jObjectData = new JSONObject();
        try {
            jObjectData.put("action", action);
            jObjectData.put("type", type);
            jObjectData.put("data", null);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        String jObjectDataString = jObjectData.toString();
        Log.i(TAG,  "Send DataTrack jObjectDataString " + jObjectDataString);
        sendMessageToDataTrack(jObjectDataString, true);
    };

    private void startScreenCapture() {
        screenVideoTrack = LocalVideoTrack.create(this, true, screenCapturer);
        screenCaptureMenuItem.setIcon(R.drawable.ic_stop_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.stop_screen_share);
        Log.i(TAG,  " startScreenCapture roomName " + roomName);
        Log.i(TAG,  " startScreenCapture tokenValue " + tokenValue);
        if(localParticipant != null){
            localParticipant.publishTrack(screenVideoTrack);
        }
    }

    private void stopScreenCapture() {
        if (screenVideoTrack != null) {
            screenVideoTrack.release();
            screenVideoTrack = null;
            screenCaptureMenuItem.setIcon(R.drawable.ic_screen_share_white_24dp);
            screenCaptureMenuItem.setTitle(R.string.share_screen);
        }
        if(room != null){
            disconnectFromRoom();
        }
    }

    @Override
    protected void onDestroy() {
        if (screenVideoTrack != null) {
            screenVideoTrack.release();
            screenVideoTrack = null;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            screenCapturerManager.unbindService();
        }
        super.onDestroy();
        if (room != null) {
            disconnectFromRoom();
        }
    }

    private void getData(String roomName1, String identity) throws JSONException {
        roomName = roomName1;
        pgsBar.setVisibility(View.VISIBLE);
        // RequestQueue initialized
        mRequestQueue = Volley.newRequestQueue(this);
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("room_name", roomName1);
        jsonBody.put("user_identity", identity);
        jsonBody.put("create_conversation", false);
        final String mRequestBody = jsonBody.toString();

        // String Request initialized
        mStringRequest = new StringRequest(Request.Method.POST, url, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.i(TAG, "Success getData :" + response);
                JSONObject jsonobject = null;
                try {
                    jsonobject = new JSONObject(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                try {
                    tokenValue = jsonobject.getString("token");
                    connectToRoom(roomName, tokenValue);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                pgsBar.setVisibility(View.GONE);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                pgsBar.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(), "Error :" + error.toString(), Toast.LENGTH_LONG).show();//display the error on screen
                Log.i(TAG, "Error getData :" + error.toString());
            }
        }) {
            @Override
            public String getBodyContentType() {
                return "application/json; charset=utf-8";
            }

            @Override
            public byte[] getBody() throws AuthFailureError {
                try {
                    return mRequestBody == null ? null : mRequestBody.getBytes("utf-8");
                } catch (UnsupportedEncodingException uee) {
                    VolleyLog.wtf("Unsupported Encoding while trying to get the bytes of %s using %s", mRequestBody, "utf-8");
                    return null;
                }
            }
        };
        mRequestQueue.add(mStringRequest);
    }

    public void connectToRoom(String roomName, String accessToken) {
        Context context = getApplicationContext();;
        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                //.dataTracks(Collections.singletonList(localDataTrack))
                .roomName(roomName)
                //.videoTracks(Collections.singletonList(screenVideoTrack))
                .build();
        room = Video.connect(context, connectOptions, this.roomListener());
    }

    public void disconnectFromRoom() {
        room.disconnect();
    }

    public void sendMessageToDataTrack(String message, Boolean disconnectRoom) {
        if (localDataTrack != null) {
            localDataTrack.send(message);
            if(disconnectRoom){
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        Log.i(TAG, "stopScreenCapture after 10 sec");
                        stopScreenCapture();
                    }
                }, 5000);
            }
        } else {
            Log.e(TAG, "Error on sendMessageToDataTrack");
        }
    }

    private void triggerScreenShare(String fcmType, String roomName) {
        Log.i(TAG,   "triggerScreenShare" + " fcmType " + fcmType + " roomId " + roomName);
        if (Objects.equals(fcmType, "REMOTE_ACCESS_REQUEST") && roomName != null) {
            try {
                getData(roomName, "TestUser");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String fcmType = intent.getStringExtra("FcmType1");
            String roomName = intent.getStringExtra("RoomId1");
            Toast.makeText(getApplicationContext(), "myReceiver onReceive roomName :" + roomName, Toast.LENGTH_LONG).show();//display the error on screen
            triggerScreenShare(fcmType, roomName);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(myReceiver, new IntentFilter("FBR-IMAGE"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(myReceiver);
    }

    private LocalParticipant.Listener localParticipantListener() {
        return new LocalParticipant.Listener() {
            @Override
            public void onDataTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalDataTrackPublication localDataTrackPublication) {
                // The data track has been published and is ready for use
                Log.i(TAG, "LocalParticipant.Listener onDataTrackPublished " + localDataTrackPublication);
                boolean isDrivingModeOn = sw.isChecked();
                if(isDrivingModeOn){
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            Log.i(TAG, "onDataTrackPublished handleDeclineEvent after 3 sec");
                            handleDeclineEvent("AUTO_DECLINE", "VEHICLE_IN_MOTION");
                        }
                    }, 3000);
                } else {
                    handleScreenShareTrigger();
                }
            }

            @Override
            public void onAudioTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalAudioTrackPublication localAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalAudioTrack localAudioTrack, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackPublished(@NonNull LocalParticipant localParticipant, @NonNull LocalVideoTrackPublication localVideoTrackPublication) {
                Log.i(TAG, "LocalParticipant.Listener onVideoTrackPublished " + localVideoTrackPublication);
            }

            @Override
            public void onVideoTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalVideoTrack localVideoTrack, @NonNull TwilioException twilioException) {
                Log.i(TAG, "LocalParticipant.Listener onVideoTrackPublicationFailed " + twilioException);
            }

            @Override
            public void onDataTrackPublicationFailed(@NonNull LocalParticipant localParticipant, @NonNull LocalDataTrack localDataTrack, @NonNull TwilioException twilioException) {
                Log.i(TAG, "LocalParticipant.Listener onDataTrackPublicationFailed " + twilioException);
            }
        };
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(@NonNull Room room) {
                Log.d(TAG,"Connected to " + room.getName());
//                boolean isDrivingModeOn = sw.isChecked();
                localParticipant = room.getLocalParticipant();
                assert localParticipant != null;
                localDataTrack = LocalDataTrack.create(getApplicationContext());
                localParticipant.setListener(localParticipantListener());
                localParticipant.publishTrack(localDataTrack);
                Toast.makeText(getApplicationContext(), "Success Connected to" + room.getName(), Toast.LENGTH_SHORT).show();//display the response on screen
            }

            @Override
            public void onConnectFailure(@NonNull Room room, @NonNull TwilioException twilioException) {
                Log.d(TAG,"onConnectFailure room name " + room.getName());
                Log.d(TAG,"onConnectFailure twilioException " + twilioException);
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {
                Log.d(TAG,"onReconnecting to " + room.getName());
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                Log.d(TAG,"onReconnected to " + room.getName());
            }

            @Override
            public void onDisconnected(@NonNull Room room, @Nullable TwilioException twilioException) {
                Log.d(TAG,"onDisconnected from " + room.getName());
                Log.d(TAG,"onDisconnected twilioException " + twilioException);
            }

            @Override
            public void onParticipantConnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG,"onParticipantConnected to " + remoteParticipant);
            }

            @Override
            public void onParticipantDisconnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG,"onParticipantDisconnected to " + remoteParticipant);
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        Log.i(TAG, "onParticipantDisconnected stopScreenCapture after 3 sec");
                        stopScreenCapture();
                    }
                }, 3000);
            }

            @Override
            public void onRecordingStarted(@NonNull Room room) {
                Log.d(TAG,"onRecordingStarted to " + room.getName());
            }

            @Override
            public void onRecordingStopped(@NonNull Room room) {
                Log.d(TAG,"onRecordingStopped to " + room.getName());
            }
        };
    }
}
