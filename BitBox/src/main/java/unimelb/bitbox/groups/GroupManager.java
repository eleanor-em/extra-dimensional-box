package unimelb.bitbox.groups;

import functional.algebraic.Maybe;
import functional.algebraic.Result;
import unimelb.bitbox.util.config.CfgValue;
import unimelb.bitbox.util.crypto.Crypto;
import unimelb.bitbox.util.crypto.CryptoException;
import unimelb.bitbox.util.crypto.SSHPublicKey;
import unimelb.bitbox.util.fs.IO;
import unimelb.bitbox.util.network.JSONDocument;
import unimelb.bitbox.util.network.JSONException;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {
    private final CfgValue<String> keyFile = CfgValue.createString("rsaKey");
    private SSHPublicKey pubKey;
    private PrivateKey privKey;

    private final Map<String, Group> groups = new ConcurrentHashMap<>();
    private static final String GROUP_FILE = "groups";

    public GroupManager() throws IOException {
        loadKeys(keyFile.get());
        loadGroups();

        keyFile.setOnChangedT(this::loadKeys);
    }

    private void loadKeys(String filename) throws IOException {
        // Load the public key
        pubKey = IO.fileToString(filename + ".pub")
                   .andThen(keyString -> Result.of(() -> new SSHPublicKey(keyString))
                                               .mapError(IOException::new)).get();

        // Load the private key
        privKey = Crypto.getRSAPrivateKey(filename).get();
    }

    private void loadGroups() throws IOException {
        try {
            String groupText = IO.fileToString(GROUP_FILE).get();
            List<JSONDocument> loadedGroups = JSONDocument.parse(groupText).get().getJSONArray("groups").get();
            for (JSONDocument groupDoc : loadedGroups) {
                Group g = Group.fromJSON(groupDoc).get();
                groups.put(g.name, g);
            }
        } catch (NoSuchFileException ignored) {
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public Result<SecretKey, CryptoException> solveChallenge(String challenge) {
        return Crypto.decryptSecretKey(challenge, privKey);
    }

    public SSHPublicKey getPubKey() {
        return pubKey;
    }

    public Group newGroup(String name, String directory) {
        Group g = new Group(name, directory);
        groups.put(name, g);
        return g;
    }

    public Maybe<Group> getGroup(String name) {
        return Maybe.of(groups.get(name));
    }
}
