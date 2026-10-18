package com.pos.scanner;

import android.content.*;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/** All companion work screens share the same app-level staff session. */
public abstract class AuthenticatedActivity extends AppCompatActivity {
    private boolean routing;
    private final BroadcastReceiver authEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent event){requireLogin();}
    };
    @Override protected void onCreate(Bundle state){super.onCreate(state);}
    @Override protected void onResume(){
        super.onResume();routing=false;
        LocalBroadcastManager.getInstance(this).registerReceiver(authEvents,new IntentFilter(WiFiCommunication.ACTION_AUTH_CHANGED));
        requireLogin();
    }
    @Override protected void onPause(){LocalBroadcastManager.getInstance(this).unregisterReceiver(authEvents);super.onPause();}
    protected boolean requireLogin(){
        if(WiFiCommunication.getInstance(this).isSignedIn())return true;
        if(!routing){routing=true;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}
        return false;
    }
}
