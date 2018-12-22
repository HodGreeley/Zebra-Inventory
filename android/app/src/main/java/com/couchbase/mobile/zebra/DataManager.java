package com.couchbase.mobile.zebra;

import android.content.Context;

import com.couchbase.lite.BasicAuthenticator;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.Expression;
import com.couchbase.lite.From;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.MessageEndpoint;
import com.couchbase.lite.MessageEndpointListener;
import com.couchbase.lite.MessageEndpointListenerConfiguration;
import com.couchbase.lite.ProtocolType;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.QueryChangeListener;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.URLEndpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

public class DataManager implements ConnectionManager.ConnectionListener {
    private static String TAG = DataManager.class.getName();
    private static String serverEndpoint = "ws://localhost:4984/inventory";

    private static ConnectionManager connectionManager;

    private Database database;
    private Context context;
    private Map<Connection, Replicator> replicators;

    public DataManager(Context context) {
        this.context = context.getApplicationContext();

        DatabaseConfiguration configuration = new DatabaseConfiguration(this.context);

        try {
            database = new Database("inventory", configuration);
        } catch (CouchbaseLiteException ex) {
            ex.printStackTrace();
        }

        replicators = new HashMap<>();
    }

    public Database getDatabase() {
        return database;
    }

    public Observable<ResultSet> fromQuery(Query query) {
        return Observable.create(emitter -> {
            QueryChangeListener listener = change -> {
                if (emitter.isDisposed()) return;

                emitter.onNext(change.getResults());
            };

            ListenerToken lt = query.addChangeListener(listener);

            emitter.setCancellable(() -> query.removeChangeListener(lt));

            query.execute();
        });
    }

    public Query createISBNQuery(String isbn) {
        Query query = QueryBuilder
                .select(SelectResult.all())
                .from(DataSource.database(database));

        if (null != isbn) {
            query = ((From)query).where(Expression.property("isbn").equalTo(Expression.string(isbn)));
        }

        return query;
    }

    public void initializePeerToPeer(Context context) {
        connectionManager = new ConnectionManager(context.getApplicationContext());
        connectionManager.setConnectionListener(this);
        connectionManager.startAdvertising();
    }

    public void startPeerReplication() {
        connectionManager.startDiscovery();
    }

    @Override
    public void onConnected(Connection connection) {
        if (connection.isActive()) {
            completeActiveConnection(connection);
            return;
        }

        completePassiveConnection(connection);
    }

    @Override
    public void onDisconnected(Connection connection) {
        if (connection.isActive()) {
            Replicator replicator = replicators.remove(connection);

            if (null != replicator) replicator.stop();
        }
    }

    private void completeActiveConnection(Connection connection) {
        MessageEndpoint messageEndpointTarget =
                new MessageEndpoint(connectionManager.getId(), connection.getEndpointId(),
                        ProtocolType.MESSAGE_STREAM, endpoint -> {
                    ActivePeerConnection activeConnection = new ActivePeerConnection(connection);

                    return activeConnection;
                });

        replicators.put(connection, startReplication(messageEndpointTarget));
    }

    private void completePassiveConnection(Connection connection) {
        PassivePeerConnection passiveConnection = new PassivePeerConnection(connection);

        MessageEndpointListenerConfiguration listenerConfig =
                new MessageEndpointListenerConfiguration(database, ProtocolType.MESSAGE_STREAM);
        MessageEndpointListener messageEndpointListener = new MessageEndpointListener(listenerConfig);

        messageEndpointListener.accept(passiveConnection);
    }

    public void startServerReplication() {
        Endpoint targetEndpoint = null;

        try {
            targetEndpoint = new URLEndpoint(new URI(serverEndpoint));
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }

        startReplication(targetEndpoint);
    }

    private Replicator startReplication(Endpoint endpoint) {
        ReplicatorConfiguration repConfig = new ReplicatorConfiguration(database, endpoint)
                .setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL)
                .setAuthenticator(new BasicAuthenticator("user", "password"))
                .setContinuous(true);
        Replicator replicator = new Replicator(repConfig);
        replicator.start();

        return replicator;
    }
}