package com.couchbase.mobile.zebra;

import android.util.Log;

import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.Payload;

public class Connection {
    private static String TAG = Connection.class.getSimpleName();

    private ConnectionsClient connectionsClient;
    private MessageReceivedListener messageReceivedListener;
    private String endpointId;
    private boolean active;

    public interface MessageReceivedListener {
        void onMessageReceived(byte[] bytes);
    }

    public Connection(ConnectionsClient connectionsClient, String endpointId, boolean active) {
        this.connectionsClient = connectionsClient;
        this.endpointId = endpointId;
        this.active = active;
    }

    public void setMessageReceivedListener(MessageReceivedListener listener) {
        messageReceivedListener = listener;
    }

    public void send(byte[] bytes) {
        Log.e(TAG, "Zebra Inventory: sending byte array, size " + bytes.length);

        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes));
    }

    public void receive(byte[] bytes) {
        Log.e(TAG, "Zebra Inventory: receiving byte array, size " + bytes.length);

        messageReceivedListener.onMessageReceived(bytes);
    }

    public String getEndpointId() { return endpointId; }

    public boolean isActive() { return active; }
}
