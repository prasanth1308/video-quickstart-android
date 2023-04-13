package com.twilio.video.examples.screencapturer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.enterprise.feedback.KeyedAppState;
import androidx.enterprise.feedback.KeyedAppStatesCallback;
import androidx.enterprise.feedback.KeyedAppStatesReporter;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
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
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.Room;
import com.twilio.video.ScreenCapturer;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** This example demonstrates how to use the screen capturer */
public class ScreenCapturerActivity extends AppCompatActivity {
    private static final String TAG = "SCREEN_CAPTURE";
    private static final int REQUEST_MEDIA_PROJECTION = 100;

//    private VideoView localVideoView;
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

    private final ScreenCapturer.Listener screenCapturerListener =
            new ScreenCapturer.Listener() {
                @Override
                public void onScreenCaptureError(String errorDescription) {
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_capturer);
//        localVideoView = (VideoView) findViewById(R.id.local_video);
        reporter = KeyedAppStatesReporter.create(this);

        if (Build.VERSION.SDK_INT >= 29) {
            screenCapturerManager = new ScreenCapturerManager(this);
        }
        pgsBar = (ProgressBar)findViewById(R.id.pBar);
        pgsBar.setVisibility(View.GONE);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                Log.i(TAG, key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
            }
        }

        String fcmType = getIntent().getStringExtra("firebaseCloudMessageType");
        roomName = getIntent().getStringExtra("roomId");

        Log.i(TAG,  " fcmType " + fcmType);
        Log.i(TAG,  " roomId " + roomName);

        if (Objects.equals(fcmType, "REMOTE_ACCESS_REQUEST") && roomName != null) {
            try {
                getData(roomName, "TestUser");
            } catch (JSONException e) {
                e.printStackTrace();
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
                        getData("TestRoom", "TestUser");
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

    private void handleScreenShareTrigger(String roomName ,String token) {
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
            requestScreenCapturePermission(roomName, token);
        } else {
            Log.d(TAG, "handleScreenShareTrigger startScreenCapture");
            startScreenCapture();
        }
    }

    private void requestScreenCapturePermission(String roomName, String tokenValue) {
        Log.d(TAG, "Requesting permission to capture screen");
        MediaProjectionManager mediaProjectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // This initiates a prompt dialog for the user to confirm screen projection.
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != AppCompatActivity.RESULT_OK) {
                Toast.makeText(
                                this,
                                R.string.screen_capture_permission_not_granted,
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }
            screenCapturer = new ScreenCapturer(this, resultCode, data, screenCapturerListener);


            Log.i(TAG, "onActivityResult START --------------------- " + data);
            String fcmType = data.getStringExtra("firebaseCloudMessageType");
            String roomId = data.getStringExtra("roomId");

            Log.i(TAG,  " fcmType " + fcmType);
            Log.i(TAG,  " roomId " + roomId);
            Log.i(TAG, "onActivityResult END --------------------- " + data);
            startScreenCapture();
        }
    }

    private void startScreenCapture() {
        screenVideoTrack = LocalVideoTrack.create(this, true, screenCapturer);
        screenCaptureMenuItem.setIcon(R.drawable.ic_stop_screen_share_white_24dp);
        screenCaptureMenuItem.setTitle(R.string.stop_screen_share);

//        localVideoView.setVisibility(View.VISIBLE);
//        screenVideoTrack.addSink(localVideoView);

        connectToRoom(roomName, tokenValue);
    }

    private void stopScreenCapture() {
        if (screenVideoTrack != null) {
//            screenVideoTrack.removeSink(localVideoView);
            screenVideoTrack.release();
            screenVideoTrack = null;
//            localVideoView.setVisibility(View.INVISIBLE);
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

    private void getData(String roomName, String identity) throws JSONException {
        pgsBar.setVisibility(View.VISIBLE);
        // RequestQueue initialized
        mRequestQueue = Volley.newRequestQueue(this);
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("room_name", roomName);
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
                    handleScreenShareTrigger(roomName, tokenValue);
                    // connectToRoom(roomName, tokenValue);
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
                .roomName(roomName)
                .videoTracks(Collections.singletonList(screenVideoTrack))
                .build();
        room = Video.connect(context, connectOptions, this.roomListener());
    }

    public void disconnectFromRoom() {
        room.disconnect();
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(Room room) {
                Log.d(TAG,"Connected to " + room.getName());
                Toast.makeText(getApplicationContext(), "Success Connected to" + room.getName(), Toast.LENGTH_LONG).show();//display the response on screen
            }

            @Override
            public void onConnectFailure(@NonNull Room room, @NonNull TwilioException twilioException) {
                Log.d(TAG,"onConnectFailure room name " + room.getName());
                Log.d(TAG,"onConnectFailure twilioException " + twilioException.getMessage());
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
                Log.d(TAG,"onDisconnected to " + room.getName());
            }

            @Override
            public void onParticipantConnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG,"onParticipantConnected to " + room.getName());
            }

            @Override
            public void onParticipantDisconnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG,"onParticipantDisconnected to " + room.getName());
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
