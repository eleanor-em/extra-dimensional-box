package unimelb.bitbox.groups;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import unimelb.bitbox.peers.Peer;
import unimelb.bitbox.util.crypto.MerkleProof;
import unimelb.bitbox.util.crypto.MerkleTree;
import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import java.util.Set;

public class Group implements IJSONData {
    private final MerkleTree<String> keySet = new MerkleTree<>();
    public final String name;
    public final String directory;

    Group(String name, String directory) {
        this.name = name;
        this.directory = directory;
    }

    static Result<Group, JSONException> fromJSON(JSONDocument doc) {
        return Result.of(() -> {
            Group g = new Group(doc.getString("name").get(),
                                doc.getString("directory").get());
            g.keySet.addAll(doc.getStringArray("members").get());
            return g;
        });
    }

    boolean contains(Peer peer) {
        return peer.getKey().matchThen(
                key -> keySet.contains(key.toString()),
                () -> false
        );
    }

    Maybe<MerkleProof> prove(Peer peer) {
        return peer.getKey().matchThen(
                key -> keySet.prove(key.toString()),
                Maybe::nothing
        );
    }

    public boolean add(Peer peer) {
        return peer.getKey().matchThen(
                key -> keySet.add(key.toString()),
                () -> false
        );
    }

    void remove(Peer peer) {
        if (peer.getKey().isJust()) {
            Set<String> elements = keySet.asSet();
            elements.remove(peer.getKey().get().toString());
            keySet.clear();

            for (String key : elements) {
                keySet.add(key);
            }
        }
    }

    @Override
    public JSONDocument toJSON() {
        return new JSONDocument()
                .append("name", name)
                .append("directory", directory)
                .append("members", keySet.asSet());
    }
}
