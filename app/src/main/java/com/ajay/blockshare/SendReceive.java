package com.ajay.blockshare;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

public class SendReceive extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_receive);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Button send_button = findViewById(R.id.send_button);
        send_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View sv) {
                // Code here executes on main thread after user presses button
            }
        });

        final Button receive_button = findViewById(R.id.receive_button);
        receive_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View rv) {
                // Code here executes on main thread after user presses button
            }
        });
    }
}
