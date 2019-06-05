package unimelb.bitbox.messages;

import unimelb.bitbox.peers.Peer;

public abstract class Response extends Message {
    final Peer peer;

    Response(String summary, Peer peer) {
        super(summary);

        this.peer = peer;
    }

    abstract void onSent();

    @Override
    public final String networkEncode() {
        onSent();
        return super.networkEncode();
    }
}
