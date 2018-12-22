package com.couchbase.mobile.zebra;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConnectionManager {
    private static final String TAG = ConnectionManager.class.getSimpleName();

    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final String ENDPOINT_NAME = "Zebra Inventory";

    private String serviceId;
    private ConnectionsClient connectionsClient;
    private ConnectionListener connectionListener;
    private Map<String, Connection> connections;

    private UUID id = UUID.randomUUID();
    private boolean activePeer = true;

    public interface ConnectionListener {
        void onConnected(Connection connection);
        void onDisconnected(Connection connection);
    }

    public ConnectionManager(Context context) {
        serviceId = context.getPackageName();
        connectionsClient = Nearby.getConnectionsClient(context.getApplicationContext());
        connections = new HashMap<>();
    }

    public void setConnectionListener(ConnectionListener listener) {
        connectionListener = listener;
    }

    public void send(String endpointId, byte[] bytes) {
        Log.e(TAG, "Zebra Inventory: sending byte array, size " + bytes.length);
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes));
    }

    public String getId() { return id.toString(); }

    public void startAdvertising() {
        connectionsClient.startAdvertising(ENDPOINT_NAME, serviceId, connectionLifecycleCallback,
                new AdvertisingOptions.Builder().setStrategy(STRATEGY).build());
    }

    private final ConnectionLifecycleCallback connectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.e(TAG, "Zebra Inventory: onConnectionInitiated");
                    if (!ENDPOINT_NAME.equals(connectionInfo.getEndpointName())) return;

                    connectionsClient.acceptConnection(endpointId, payloadCallback);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    Log.e(TAG, "Zebra Inventory: onConnectionResult, activePeer = " + activePeer);

                    if (result.getStatus().isSuccess()) {
                        connectionsClient.stopDiscovery();
                        //connectionsClient.stopAdvertising();
                        Connection connection = new Connection(connectionsClient, endpointId, activePeer);

                        connections.put(endpointId, connection);
                        connectionListener.onConnected(connection);
                    }
                }

                @Override
                public void onDisconnected(String endpointId) {
                    Connection connection = connections.remove(endpointId);
                    connectionListener.onDisconnected(connection);
                }
            };

    public void startDiscovery() {
        connectionsClient.startDiscovery(serviceId, endpointDiscoveryCallback,
                new DiscoveryOptions.Builder().setStrategy(STRATEGY).build());
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    Log.e(TAG, "Zebra Inventory: onEndpointFound " + endpointId);

                    connectionsClient.requestConnection(ENDPOINT_NAME, endpointId, connectionLifecycleCallback);
                    activePeer = false;
                }

                @Override
                public void onEndpointLost(String endpointId) {
                    Log.e(TAG, "Zebra Inventory: onEndpointLost");
                }
            };

    private final PayloadCallback payloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    Connection connection = connections.get(endpointId);

                    if (null == connection) return;

                    byte[] bytes = payload.asBytes();
                    Log.e(TAG, "Zebra Inventory: payload received, size " + bytes.length);
                    connection.receive(payload.asBytes());
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {}
            };
}
