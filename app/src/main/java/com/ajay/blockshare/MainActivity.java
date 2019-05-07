package com.ajay.blockshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.StrictMode;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    Context context = this;
    private Button access_button;
    int READ_REQUEST_CODE = 43;
    Uri bcuri;
    String bcname;
    String bckey;
    String[] str_arr;
    String dname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        access_button = findViewById(R.id.access_asset_button);

        final Intent sendReceiveIntent = new Intent(this, SendReceive.class);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(sendReceiveIntent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        access_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                Toast toast = Toast.makeText(context, "Select Encrypted Block Contract", Toast.LENGTH_SHORT);
                toast.show();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE
                && resultCode == Activity.RESULT_OK
                && resultData != null) {
            bcuri = resultData.getData();
            if (bcuri.getScheme().equals("content")) {
                Cursor cursor = getContentResolver().query(bcuri, null, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        bcname = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        Log.e("URI", bcname);
                    }
                } finally {
                    cursor.close();
                }
            }
            boolean check = checkBCValidity();
            if(check)
                acquireAssets();
            else {
                Toast fail_toast = Toast.makeText(context, "Attempt Unsuccessful", Toast.LENGTH_SHORT);
                fail_toast.show();
            }

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected boolean checkBCValidity() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        Log.e("Error", "X");
        URL url = null;
        try {
            url = new URL("http://192.168.0.105/main.txt");
        } catch (MalformedURLException e) {
            Log.e("Error", "1");
        }
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(url.openStream()));
            if(in != null)
                Log.e("Error", "Y");
        } catch (IOException e) {
            e.printStackTrace();
        }
        String inputLine;
        while (true) {
            try {
                if (!((inputLine = in.readLine()) != null)) break;
                str_arr = inputLine.split(": ", 2);
                dname = str_arr[0];
                dname += ".json";
                str_arr[0] += ".json.enc";
                if(str_arr[0].equals(bcname))
                {
                    bckey = str_arr[1];
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            } catch (IOException e) {
                Log.e("Error", "2");
            }
        }
        try {
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;

    }

    public void acquireAssets() {
        File f = new File("//sdcard//Download//Nearby//" + bcname);
        InputStream is = null;
        try {
            is = new FileInputStream(f);
        } catch (FileNotFoundException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
        byte[] textCryp = null;
        try {
            textCryp = new byte[is.available()];
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try {
            is.read(textCryp);
            is.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Cipher cipher;
        byte[] decodedKey = Base64.getDecoder().decode(bckey);
        SecretKey key = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
        byte[] decrypted = null;
        try {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            decrypted = cipher.doFinal(textCryp);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            saveFile(decrypted, "//sdcard//Download//" + dname);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Toast fail_toast = Toast.makeText(context, "Block Contract Successfully Decrypted", Toast.LENGTH_SHORT);
        fail_toast.show();

    }
    private void saveFile(byte[] bytes, String filenamepath) throws IOException {

        FileOutputStream fos = new FileOutputStream(filenamepath);
        fos.write(bytes);
        fos.close();

    }
}
