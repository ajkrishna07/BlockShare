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

import java.io.File;
import java.nio.charset.StandardCharsets;

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
            };
}
