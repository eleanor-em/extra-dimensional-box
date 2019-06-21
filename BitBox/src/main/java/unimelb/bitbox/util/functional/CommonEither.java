package unimelb.bitbox.util.functional;

import functional.algebraic.Either;

import java.util.function.Function;

/**
 * An {@link Either} where each element extends a base class.
 */
public class CommonEither<L extends I, R extends I, I> {
    public final Either<L, R> val;

    private CommonEither(Either<L, R> val) {
        this.val = val;
    }

    public <T> T collapse(Function<I, T> func) {
        return val.matchThen(func, func);
    }

    public static <L extends I, R extends I, I> CommonEither<L, R, I> right(R val) {
        return new CommonEither<>(Either.right(val));
    }
    public static <L extends I, R extends I, I> CommonEither<L, R, I> left(L val) {
        return new CommonEither<>(Either.left(val));
    }
}
