package unimelb.bitbox.util.functional.algebraic;

import unimelb.bitbox.util.functional.combinator.Combinators;

import java.util.function.Consumer;

public class ChainedEither<A extends T, B extends T, C extends T, T> {
    private Either<A, Either<B, C>> val = null;

    private ChainedEither() {}

    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> left(A val) {
        ChainedEither<A, B, C, T> res = new ChainedEither<>();
        res.val = Either.left(val);
        return res;
    }
    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> middle(B val) {
        ChainedEither<A, B, C, T> res = new ChainedEither<>();
        res.val = Either.right(Either.left(val));
        return res;
    }
    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> right(C val) {
        ChainedEither<A, B, C, T> res = new ChainedEither<>();
        res.val = Either.right(Either.right(val));
        return res;
    }

    public T resolve() {
        return val.matchThen(Combinators::id, val -> val.matchThen(Combinators::id, Combinators::id));
    }

    public void match(Consumer<? super A> left, Consumer<? super B> middle, Consumer<? super C> right) {
        val.match(left, either -> either.match(middle, right));
    }
}
