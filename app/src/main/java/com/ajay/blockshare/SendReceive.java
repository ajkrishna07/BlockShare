package com.ajay.blockshare;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.content.Intent;

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
                final Intent sendIntent = new Intent(getApplicationContext(), Send.class);
                startActivity(sendIntent);
            }
        });

        final Button receive_button = findViewById(R.id.receive_button);
        receive_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View rv) {
                final Intent receiveIntent = new Intent(getApplicationContext(), Receive.class);
                startActivity(receiveIntent);
            }
        });
    }
}
