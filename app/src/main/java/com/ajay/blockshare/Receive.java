package com.ajay.blockshare;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Button;
import android.view.View;
import android.content.Intent;
import android.Manifest;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.collection.SimpleArrayMap;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.content.Context;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Receive extends AppCompatActivity {
    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
            };
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;
    private static final Strategy STRATEGY = Strategy.P2P_STAR;
    private ConnectionsClient connectionsClient;
    private final String codeName = CodenameGenerator.generate();
    private String opponentEndpointId;
    private String opponentName;
    private Context context;
    private TextView statusMessage;
    private NotificationManager notificationManager;
    private String CHANNEL_ID = "default";
    private String jsonfilename;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive);
        statusMessage = findViewById(R.id.statmsg);
        context = this;
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Transfer";
            String description = "Shows the progress of updates";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
        connectionsClient = Nearby.getConnectionsClient(this);

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }

        startAdvertising();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onDestroy() {
        connectionsClient.stopAllEndpoints();
        super.onDestroy();
    }

    /** Returns true if the app was granted all the permissions. Otherwise, returns false. */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Handles user acceptance (or denial) of our permission request. */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, R.string.error_missing_permissions, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        recreate();
    }

    private void startAdvertising() {
        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();
        connectionsClient.startAdvertising(codeName, getPackageName(), connectionLifecycleCallback, advertisingOptions);
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    connectionsClient.requestConnection(codeName, endpointId, connectionLifecycleCallback);
                }

                @Override
                public void onEndpointLost(String endpointId) {}
            };

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo info) {
                    new AlertDialog.Builder(context)
                            .setTitle("Accept connection to " + info.getEndpointName())
                            .setMessage("Confirm the code matches on both devices: " + info.getAuthenticationToken())
                            .setPositiveButton(
                                    "Accept",
                                    (DialogInterface dialog, int which) ->
                                            // The user confirmed, so we can accept the connection.
                                            connectionsClient.acceptConnection(endpointId, payloadCallback))
                            .setNegativeButton(
                                    android.R.string.cancel,
                                    (DialogInterface dialog, int which) ->
                                            // The user canceled, so we should reject the connection.
                                            connectionsClient.rejectConnection(endpointId))
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                    opponentName = info.getEndpointName();
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        connectionsClient.stopDiscovery();
                        connectionsClient.stopAdvertising();
                        opponentEndpointId = endpointId;
                        statusMessage.setText("Connected to " + opponentEndpointId);
                        Toast toast = Toast.makeText(context, "Connection established with " + opponentEndpointId, Toast.LENGTH_LONG);
                        toast.show();
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    statusMessage.setText("Connection lost");
                    Toast toast = Toast.makeText(context, "Connection lost", Toast.LENGTH_LONG);
                    toast.show();
                }
            };

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                String filename = "generic.jpg";
                File payloadFile;
                private final SimpleArrayMap<Long, NotificationCompat.Builder> incomingPayloads = new SimpleArrayMap<>();
                private final SimpleArrayMap<Long, NotificationCompat.Builder> outgoingPayloads = new SimpleArrayMap<>();

                private void sendPayload(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        // No need to track progress for bytes.
                        return;
                    }

                    // Build and start showing the notification.
                    NotificationCompat.Builder notification = buildNotification(payload, /*isIncoming=*/ false);
                    notificationManager.notify((int) payload.getId(), notification.build());

                    // Add it to the tracking list so we can update it.
                    outgoingPayloads.put(payload.getId(), notification);
                }

                private NotificationCompat.Builder buildNotification(Payload payload, boolean isIncoming) {
                    NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                            .setContentTitle(isIncoming ? "Receiving..." : "Sending...")
                            .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark);
                    boolean indeterminate = false;
                    if (payload.getType() == Payload.Type.STREAM) {
                        // We can only show indeterminate progress for stream payloads.
                        indeterminate = true;
                    }
                    notification.setProgress(100, 0, indeterminate);
                    return notification;
                }

                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    if (payload.getType() == Payload.Type.BYTES) {
                        filename = new String(payload.asBytes());
                        Log.e("My App", "Byte Received");
                        return;
                    } else if(payload.getType() == Payload.Type.FILE) {
                        Log.e("My App", "File Received");
                        payloadFile = payload.asFile().asJavaFile();

                        // Build and start showing the notification.
                        NotificationCompat.Builder notification = buildNotification(payload, true /*isIncoming*/);
                        notificationManager.notify((int) payload.getId(), notification.build());

                        // Add it to the tracking list so we can update it.
                        incomingPayloads.put(payload.getId(), notification);
                    }
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    long payloadId = update.getPayloadId();
                    NotificationCompat.Builder notification = null;
                    if (incomingPayloads.containsKey(payloadId)) {
                        notification = incomingPayloads.get(payloadId);
                        if (update.getStatus() != PayloadTransferUpdate.Status.IN_PROGRESS) {
                            // This is the last update, so we no longer need to keep track of this notification.
                            incomingPayloads.remove(payloadId);
                        }
                    } else if (outgoingPayloads.containsKey(payloadId)) {
                        notification = outgoingPayloads.get(payloadId);
                        if (update.getStatus() != PayloadTransferUpdate.Status.IN_PROGRESS) {
                            // This is the last update, so we no longer need to keep track of this notification.
                            outgoingPayloads.remove(payloadId);
                        }
                    }

                    if (notification == null) {
                        return;
                    }

                    switch (update.getStatus()) {
                        case PayloadTransferUpdate.Status.IN_PROGRESS:
                            long size = update.getTotalBytes();
                            if (size == -1) {
                                // This is a stream payload, so we don't need to update anything at this point.
                                return;
                            }
                            int percentTransferred =
                                    (int) (100.0 * (update.getBytesTransferred() / (double) update.getTotalBytes()));
                            notification.setProgress(100, percentTransferred, /* indeterminate= */ false);
                            break;
                        case PayloadTransferUpdate.Status.SUCCESS:
                            // SUCCESS always means that we transferred 100%.
                            notification.setProgress(100, 100, /* indeterminate= */ false).setContentText("Transfer complete!");
                            Log.e("My App", "Success");
                            if(payloadFile != null) {
                                payloadFile.renameTo(new File(payloadFile.getParentFile(), filename));
                                if(filename.substring(filename.length()-4).equals("json")) {
                                    jsonfilename = filename;
                                    Log.e("filename", filename);
                                }
                                else if(filename.substring(filename.length()-3).equals("enc")) {
                                    JSONObject jsonParser = null;
                                    String data = null;
                                    BufferedReader br = null;
                                    String key;
                                    File file = new File("//sdcard//Download//Nearby//" + jsonfilename);
                                    try {
                                        br = new BufferedReader(new FileReader(file));
                                        data = br.readLine();
                                        Log.e("JSON", data);
                                        jsonParser = new JSONObject(data);
                                        key = (String) jsonParser.get("key");
                                        byte[] decodedKey = Base64.getDecoder().decode(key);
                                        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
                                        byte[] decrypted = decryptPdfFile(secretKey, "//sdcard//Download//Nearby//" + filename);
                                        saveFile(decrypted, "//sdcard//Download//Nearby//" + jsonParser.get("fname"));
                                        br.close();
                                    } catch (FileNotFoundException e) {
                                        e.printStackTrace();
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                }
                            }
                            break;
                        case PayloadTransferUpdate.Status.FAILURE:
                        case PayloadTransferUpdate.Status.CANCELED:
                            notification.setProgress(0, 0, false).setContentText("Transfer failed");
                            break;
                        default:
                            // Unknown status.
                    }

                    notificationManager.notify((int) payloadId, notification.build());
                }
                private byte[] decryptPdfFile(Key key, String filepath) throws IOException {
                    Cipher cipher;
                    byte[] textCryp = getEncryptedFile(filepath);
                    byte[] decrypted = null;
                    try {
                        cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                        cipher.init(Cipher.DECRYPT_MODE, key);
                        decrypted = cipher.doFinal(textCryp);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return decrypted;
                }

                private byte[] getEncryptedFile(String filename) throws IOException {

                    File f = new File(filename);
                    InputStream is = null;
                    try {
                        is = new FileInputStream(f);
                    } catch (FileNotFoundException e2) {
                        // TODO Auto-generated catch block
                        e2.printStackTrace();
                    }
                    byte[] content = null;
                    try {
                        content = new byte[is.available()];
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    try {
                        is.read(content);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    is.close();

                    return content;
                }

                private void saveFile(byte[] bytes, String filenamepath) throws IOException {

                    FileOutputStream fos = new FileOutputStream(filenamepath);
                    fos.write(bytes);
                    fos.close();

                }
            };
}
