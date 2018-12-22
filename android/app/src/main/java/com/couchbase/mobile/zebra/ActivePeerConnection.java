package com.couchbase.mobile.zebra;

import com.couchbase.lite.Message;
import com.couchbase.lite.MessageEndpointConnection;
import com.couchbase.lite.MessagingCloseCompletion;
import com.couchbase.lite.MessagingCompletion;
import com.couchbase.lite.ReplicatorConnection;

public class ActivePeerConnection implements MessageEndpointConnection {
    private ReplicatorConnection replicatorConnection;
    private Connection connection;

    public ActivePeerConnection(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void open(ReplicatorConnection replicatorConnection, MessagingCompletion completion) {
        this.replicatorConnection = replicatorConnection;
        connection.setMessageReceivedListener(bytes -> replicatorConnection.receive(Message.fromData(bytes)));
        completion.complete(true, null);
    }

    @Override
    public void close(Exception error, MessagingCloseCompletion completion) {

    }

    @Override
    public void send(Message message, MessagingCompletion completion) {
        connection.send(message.toData());
        completion.complete(true, null);
    }
}
