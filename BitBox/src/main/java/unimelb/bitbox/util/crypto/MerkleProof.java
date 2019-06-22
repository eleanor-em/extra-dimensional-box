package unimelb.bitbox.util.crypto;

import unimelb.bitbox.util.network.IJSONData;
import unimelb.bitbox.util.network.JSONDocument;

import java.util.Base64;
import java.util.List;

public class MerkleProof implements IJSONData {
    final byte[] rootHash;
    final List<ProofNode> nodes;

    MerkleProof(byte[] rootHash, List<ProofNode> nodes) {
        this.rootHash = rootHash;
        this.nodes = nodes;
    }

    @Override
    public JSONDocument toJSON() {
        return new JSONDocument().append("root", Base64.getEncoder().encodeToString(rootHash))
                                 .append("proof", nodes);
    }
}
