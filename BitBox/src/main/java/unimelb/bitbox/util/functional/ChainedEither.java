package unimelb.bitbox.util.functional;

import functional.algebraic.Either;
import functional.combinator.Combinators;

import java.util.function.Consumer;

/**
 * A three-way {@link Either} where each type inherits from a common base clase.
 *
 * @author Eleanor McMurtry
 */
public class ChainedEither<A extends T, B extends T, C extends T, T> {
    private final Either<A, Either<B, C>> val;

    private ChainedEither(Either<A, Either<B, C>> val) {
        this.val = val;
    }

    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> left(A val) {
        return new ChainedEither<>(Either.left(val));
    }
    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> middle(B val) {
        return new ChainedEither<>(Either.right(Either.left(val)));
    }
    public static <A extends T, B extends T, C extends T, T> ChainedEither<A, B, C, T> right(C val) {
        return new ChainedEither<>(Either.right(Either.right(val)));
    }

    public T resolve() {
        return val.matchThen(Combinators::id, val -> val.matchThen(Combinators::id, Combinators::id));
    }

    public void match(Consumer<? super A> left, Consumer<? super B> middle, Consumer<? super C> right) {
        val.match(left, either -> either.match(middle, right));
    }
}
