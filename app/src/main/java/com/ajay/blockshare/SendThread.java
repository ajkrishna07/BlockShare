package com.ajay.blockshare;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.Payload;

public class SendThread extends Thread {

    private String opponentEndpointId;
    private Payload payload;
    private ConnectionsClient connectionsClient;

    SendThread(ConnectionsClient cc, String epid, Payload pl) {
        this.connectionsClient = cc;
        this.opponentEndpointId = epid;
        this.payload = pl;
    }
    public void run() {
        connectionsClient.sendPayload(opponentEndpointId, payload);
    }
}
