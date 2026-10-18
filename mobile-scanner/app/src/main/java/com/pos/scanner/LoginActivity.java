package com.pos.scanner;

import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;

public final class LoginActivity extends AppCompatActivity {
    private WiFiCommunication wifi;
    private TextInputEditText email,password;
    private TextView status;
    private Button signIn;
    private View progress;
    private boolean busy;
    private final BroadcastReceiver connection=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent event){if(!wifi.isConnected())finish();}
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_login);
        wifi=WiFiCommunication.getInstance(this);
        email=findViewById(R.id.loginEmail);password=findViewById(R.id.loginPassword);status=findViewById(R.id.loginStatus);
        signIn=findViewById(R.id.loginSubmit);progress=findViewById(R.id.loginProgress);
        signIn.setOnClickListener(v->login());findViewById(R.id.loginDisconnect).setOnClickListener(v->{wifi.closeConnection();finish();});
        password.setOnEditorActionListener((v,action,event)->{login();return true;});
        LocalBroadcastManager.getInstance(this).registerReceiver(connection,new IntentFilter("WIFI_CONNECTED"));
        if(!wifi.isConnected()||wifi.isSignedIn())finish();
    }
    private void login(){
        if(busy)return;
        String address=email.getText().toString().trim();char[] secret=password.getText().toString().toCharArray();
        if(address.isEmpty()||secret.length==0){Arrays.fill(secret,'\0');status.setText("Enter your staff email and password.");return;}
        busy=true;signIn.setEnabled(false);email.setEnabled(false);password.setText("");password.setEnabled(false);progress.setVisibility(View.VISIBLE);
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(),0);
        status.setText("Signing in...");
        wifi.login(address,secret,response->{
            if(isDestroyed())return;
            busy=false;signIn.setEnabled(true);email.setEnabled(true);password.setEnabled(true);progress.setVisibility(View.GONE);
            if(response.get("ok").getAsBoolean())finish();
            else status.setText(response.has("message")?response.get("message").getAsString():"Sign-in failed. Please retry.");
        });
    }
    @Override public void onBackPressed(){wifi.closeConnection();super.onBackPressed();}
    @Override protected void onDestroy(){LocalBroadcastManager.getInstance(this).unregisterReceiver(connection);super.onDestroy();}
}
