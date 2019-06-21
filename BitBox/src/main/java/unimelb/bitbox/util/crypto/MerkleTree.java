package unimelb.bitbox.util.crypto;

import functional.algebraic.Maybe;
import functional.combinator.Curried;
import org.jetbrains.annotations.NotNull;
import unimelb.bitbox.util.functional.CommonEither;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * A Merkle tree, using SHA-512.
 * Allows logarithmic insertion, lookup, proof generation, and proof verification.
 * @param <E> the type to store; must implement {@link Serializable}
 *
 * @authoer Eleanor McMurtry
 */
public class MerkleTree<E extends Serializable> {
    private static final int HASH_SIZE = 512;

    static byte[] hash(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-" + HASH_SIZE);
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static byte[] hash(Serializable input) {
        // Convert the object to a byte stream
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             ObjectOutputStream objStream = new ObjectOutputStream(stream)) {
            objStream.writeObject(input);
            objStream.flush();

            // Compute hash
            return hash(stream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Maybe<InternalNode<E>> root = Maybe.nothing();

    /**
     * Adds the element to the tree.
     * @param element the element to add
     * @return whether the element was successfully added
     */
    public boolean add(E element) {
        LeafNode<E> newLeaf = new LeafNode<>(element);
        if (root.isJust()) {
            InternalNode<E> node = root.get();
            for (int i = 0; i < HASH_SIZE; ++i) {
                if (node.isLeaf()) {
                    return node.split(newLeaf, i);
                } else {
                    byte[] bits = node.getSkippedBits().get();
                    for (byte bit : bits) {
                        if (newLeaf.getBit(i) != bit) {
                            return node.split(newLeaf, i);
                        } else {
                            ++i;
                        }
                    }
                    boolean left = newLeaf.getBit(i) == 0;
                    node = left ? node.getLeft().get() : node.getRight().get();
                }
            }
            return false;
        } else {
            root = Maybe.just(new InternalNode<>(newLeaf));
            return true;
        }
    }

    /**
     * @return whether the element is in the tree
     */
    public boolean contains(E element) {
        if (root.isJust()) {
            InternalNode<E> node = root.get();
            LeafNode<E> leaf = new LeafNode<>(element);
            for (int i = 0; i < HASH_SIZE; ++i) {
                if (node.isLeaf()) {
                    return node.leafEquals(leaf);
                } else {
                    byte[] bits = node.getSkippedBits().get();
                    for (byte bit : bits) {
                        if (leaf.getBit(i) != bit) {
                            return false;
                        } else {
                            ++i;
                        }
                    }
                    boolean left = leaf.getBit(i) == 0;
                    Maybe<InternalNode<E>> nextNode = left ? node.getLeft() : node.getRight();
                    if (nextNode.isJust()) {
                        node = nextNode.get();
                    } else {
                        return false;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    /**
     * If the element is in the tree, produces a proof that it is.
     * @param element the element to check
     * @return a proof that the element is in the tree, or Maybe.nothing() if it isn't
     */
    public Maybe<List<ProofNode>> prove(E element) {
        if (root.isJust()) {
            InternalNode<E> node = root.get();
            LeafNode<E> leaf = new LeafNode<>(element);
            List<ProofNode> proof = new LinkedList<>();

            for (int i = 0; i < HASH_SIZE; ++i) {
                if (node.isLeaf()) {
                    if (node.leafEquals(leaf)) {
                        return Maybe.just(proof);
                    } else {
                        return Maybe.nothing();
                    }
                } else {
                    byte[] bits = node.getSkippedBits().get();
                    for (byte bit : bits) {
                        if (leaf.getBit(i) != bit) {
                            return Maybe.nothing();
                        } else {
                            ++i;
                        }
                    }
                    // Whether this node is a left node
                    boolean left = leaf.getBit(i) == 0;
                    Maybe<InternalNode<E>> nextNode = left ? node.getLeft() : node.getRight();
                    if (nextNode.isJust()) {
                        node = nextNode.get();

                        InternalNode<E> sibling = node.getSibling().get();
                        // Sibling is the opposite of this node
                        proof.add(0, new ProofNode(!left, sibling.getHash()));
                    } else {
                        return Maybe.nothing();
                    }
                }
            }

            return Maybe.nothing();
        } else {
            return Maybe.nothing();
        }
    }

    /**
     * Verifies the given proof for the given element.
     * @param element the element to check
     * @param proof the proof to check
     * @return whether the proof is valid
     */
    public boolean verify(E element, List<ProofNode> proof) {
        if (root.isJust()) {
            byte[] hash = MerkleTree.hash(element);

            for (ProofNode node : proof) {
                hash = node.hashWith(hash);
            }
            return Arrays.equals(hash, root.get().getHash());
        } else {
            return false;
        }
    }


    @Override
    public String toString() {
        if (root.isJust()) {
            List<InternalNode<E>> nodes = new LinkedList<>();
            nodes.add(root.get());
            StringBuilder ret = new StringBuilder();

            // Breadth-first search
            while (!nodes.isEmpty()) {
                InternalNode<E> node = nodes.remove(0);
                ret.append(node.toString());

                if (!node.isLeaf()) {
                    nodes.addAll(node.getChildren().get().asList());
                }
                if (!nodes.isEmpty()) {
                    ret.append("\n");
                }
            }
            return ret.toString();
        } else {
            return "(Empty)";
        }
    }

    /**
     * Unit tests for the class.
     */
    public static void main(String[] args) {
        int count = 1000000;

        List<String> insert = new ArrayList<>();
        List<String> notPresent = new ArrayList<>();

        String last = "";
        for (int i = 0; i < count; ++i) {
            last = "cbca" + i;
            insert.add(last);
            notPresent.add("Goodbye" + i);
        }


        MerkleTree<String> tree = new MerkleTree<>();
        for (String s : insert) {
            tree.add(s);
        }
        for (String s : insert) {
            System.out.println("checking " + s);
            assert tree.contains(s);
        }
        for (String s : notPresent) {
            System.out.println("checking missing: " + s);
            assert !tree.contains(s);
        }


        List<ProofNode> proof = tree.prove(last).get();
        proof.forEach(System.out::println);
        System.out.println("Proof has " + proof.size() + " elements");
        assert tree.verify(last, proof);

        proof.remove(0);
        assert !tree.verify(last, proof);

        System.out.println("Success!");
    }
}

class ProofNode {
    private final boolean left;
    private final byte[] hash;

    ProofNode(boolean left, byte[] hash) {
        this.left = left;
        this.hash = hash;
    }

    public byte[] hashWith(byte[] other) {
        byte[] leftHash = left ? hash : other;
        byte[] rightHash = left ? other : hash;
        byte[] both = ChildNodePair.concat(leftHash, rightHash);
        return MerkleTree.hash(both);
    }

    @Override
    public String toString() {
        return "(" + (left ? "left:  " : "right: ")
                + Base64.getEncoder().encodeToString(hash)
                + ")";
    }
}

@FunctionalInterface
interface IMerkleHashable {
    byte[] getHash();
}

class ChildNodePair<E extends Serializable> implements IMerkleHashable {
    public final InternalNode<E> l;
    public final InternalNode<E> r;
    public final byte[] bits;
    public final int index;

    ChildNodePair(InternalNode<E> l, InternalNode<E> r, byte[] bits, int index) {
        this.l = l;
        this.r = r;
        this.bits = bits;
        this.index = index;
    }

    @Override
    public byte[] getHash() {
        return MerkleTree.hash(concat(l.getHash(), r.getHash()));
    }

    public List<InternalNode<E>> asList() {
        return Arrays.asList(l, r);
    }

    public static byte[] concat(byte[] a, byte[] b){
        int length = a.length + b.length;
        byte[] result = Arrays.copyOf(a, length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}

class InternalNode<E extends Serializable> implements IMerkleHashable {
    private CommonEither<ChildNodePair<E>, LeafNode<E>, IMerkleHashable> child;
    private Maybe<InternalNode<E>> parent = Maybe.nothing();
    private byte[] hash;

    InternalNode(LeafNode<E> leaf) {
        child = CommonEither.right(leaf);
        calculateHash();
    }

    private InternalNode(InternalNode<E> old) {
        hash = old.hash;
        parent = Maybe.just(old);
        child = old.child;
    }

    public boolean isLeaf() {
        return child.val.isRight();
    }

    public Maybe<InternalNode<E>> getLeft() {
        return child.val.matchThen(pair -> Maybe.just(pair.l), __ -> Maybe.nothing());
    }
    public Maybe<InternalNode<E>> getRight() {
        return child.val.matchThen(pair -> Maybe.just(pair.r), __ -> Maybe.nothing());
    }
    public Maybe<byte[]> getSkippedBits() {
        return child.val.matchThen(pair -> Maybe.just(pair.bits), __ -> Maybe.nothing());
    }

    public Maybe<InternalNode<E>> getSibling() {
        return parent.andThen(val -> val.child.val.matchThen(pair -> {
            if (pair.l.equals(this)) {
                return Maybe.just(pair.r);
            } else {
                return Maybe.just(pair.l);
            }
        }, Curried.constant(Maybe.nothing())));
    }
    public Maybe<ChildNodePair<E>> getChildren() {
        return child.val.matchThen(
                Maybe::just,
                Curried.constant(Maybe.nothing())
        );
    }

    public boolean split(LeafNode<E> element, int index) {
        InternalNode<E> newNode = new InternalNode<>(element);
        newNode.parent = Maybe.just(this);
        InternalNode<E> oldNode = new InternalNode<>(this);

        return child.val.matchThen(
                pair -> {
                    // Whether the new leaf node should be inserted on the left
                    boolean left = element.getBit(index) == 0;

                    // How many bits will be left over in the old node after the split
                    byte[] remainingBits;
                    if (index + 1 - pair.index > pair.bits.length) {
                        remainingBits = new byte[0];
                    } else {
                        // index + 1 because the splitting means that we already accounted for bits[index]
                        // pair.index is the offset of the stored bits, so we need to subtract that
                        remainingBits = Arrays.copyOfRange(pair.bits, index + 1 - pair.index, pair.bits.length);
                    }
                    byte[] ourBits = Arrays.copyOf(pair.bits, index - pair.index);

                    oldNode.child = CommonEither.left(new ChildNodePair<>(pair.l, pair.r, remainingBits, index + 1));
                    pair.l.parent = Maybe.just(oldNode);
                    pair.r.parent = Maybe.just(oldNode);

                    if (left) {
                        child = CommonEither.left(new ChildNodePair<>(newNode, oldNode, ourBits, pair.index));
                    } else {
                        child = CommonEither.left(new ChildNodePair<>(oldNode, newNode, ourBits, pair.index));
                    }
                    oldNode.calculateHashRecursively();
                    return true;
                },
                leaf -> {
                    // If we are a leaf node, splitting is pretty easy because we can just compare
                    int compare = element.compare(leaf, index);
                    if (compare == 0) {
                        return false;
                    } else {
                        // Compare returns the index of the first non-matching bit, plus 1
                        int skipLength = Math.max(Math.abs(compare) - 1 - index, 0);

                        byte[] bits = new byte[skipLength];
                        for (int i = 0; i < skipLength; ++i) {
                            bits[i] = (byte) element.getBit(index + i);
                        }

                        if (compare < 0) {
                            child = CommonEither.left(new ChildNodePair<>(newNode, oldNode, bits, index));
                        } else {
                            child = CommonEither.left(new ChildNodePair<>(oldNode, newNode, bits, index));
                        }
                        calculateHashRecursively();
                        return true;
                    }
                }
        );
    }

    private void calculateHash() {
        hash = child.collapse(IMerkleHashable::getHash);
    }

    private void calculateHashRecursively() {
        hash = child.collapse(IMerkleHashable::getHash);
        if (parent.isJust()) {
            parent.get().calculateHashRecursively();
        }
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return (isLeaf() ? "(Leaf) " + child.val.fromRight(LeafNode.empty()).getElement() + " " : "(Internal) ")
                + Base64.getEncoder().encodeToString(getHash())
                + parent.map(val -> " (parent: " + val + ")").orElse("");
    }

    public boolean leafEquals(LeafNode<E> rhs) {
        if (isLeaf()) {
            return child.val.fromRight(LeafNode.empty()).equals(rhs);
        } else {
            return false;
        }
    }
}

class LeafNode<E extends Serializable> implements IMerkleHashable {
    private final E element;
    private final byte[] hash;

    public static <E extends Serializable> LeafNode<E> empty() {
        return new LeafNode<>(null);
    }

    LeafNode(E element) {
        this.element = element;
        if (element == null) {
            hash = null;
        } else {
            hash = MerkleTree.hash(element);
        }
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    public E getElement() {
        return element;
    }

    public int getBit(int bitNumber) {
        int byteNumber = bitNumber / 8;
        assert byteNumber < hash.length;

        int bitOffset  = bitNumber % 8;
        // Add 128 because Java bytes are signed
        return Math.min((hash[byteNumber] + 128) & (1 << bitOffset), 1);
    }

    public int compare(@NotNull LeafNode<E> o, int startIndex) {
        for (int i = startIndex; i < 8 * Math.min(hash.length, o.hash.length); ++i) {
            if (getBit(i) == 0 && o.getBit(i) == 1) {
                return -(i + 1);
            } else if (getBit(i) == 1 && o.getBit(i) == 0) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object rhs) {
        return rhs instanceof LeafNode && Arrays.equals(((LeafNode) rhs).getHash(), getHash());
    }
}