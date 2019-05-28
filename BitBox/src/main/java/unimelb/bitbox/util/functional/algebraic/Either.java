/*
 * Copyright 2019 Zoey Hewll
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package unimelb.bitbox.util.functional.algebraic;

import unimelb.bitbox.util.functional.throwing.ThrowingConsumer;
import unimelb.bitbox.util.functional.throwing.ThrowingFunction;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>A container which holds a value of one of two types; and provides pattern matching to safely unwrap the data.
 * By convention, the Left constructor is used to hold an error value and the Right constructor is used to hold a correct value.
 * <p>
 * <p>The type is sealed with a private constructor to ensure the only subclasses are {@link Left} and {@link Right}.
 * <p>
 * <p>Instances of the same structure containing the same data are considered equal.
 *
 * @param <L> The left alternative type
 * @param <R> The right alternative type
 * @author Zoey Hewll
 */
public abstract class Either<L, R>
{
    /**
     * Private constructor to seal the type.
     */
    private Either() {}


    public abstract int hashCode();

    public abstract boolean equals(Object o);

    public abstract String toString();

    /**
     * Match on the contained value, applying the function pertaining to the contained type, and returning the result.
     * Both functions must have the same return and throw types.
     *
     * @param lf  The function to apply to the contained value if it is a {@link Left} value.
     * @param rf  The function to apply to the contained value if it is a {@link Right} value.
     * @param <T> The return type of the functions.
     * @param <E> The type of the error thrown by the functions.
     * @return The value returned by the matched function.
     * @throws E The error thrown by the functions.
     */
    public abstract <T, E extends Throwable> T unsafeMatch(ThrowingFunction<? super L, ? extends T, ? extends E> lf, ThrowingFunction<? super R, ? extends T, ? extends E> rf) throws E;

    /**
     * Match on the contained value, performing the operation pertaining to the contained type.
     * Both operation must throw the same types.
     *
     * @param lf The operation to perform on the contained value if it is a {@link Left} value.
     * @param rf The operation to perform on the contained value if it is a {@link Right} value.
     * @param <E> The type of the error thrown by the operation.
     * @throws E The error thrown by the operation.
     */
    public abstract <E extends Throwable> void unsafeMatch(ThrowingConsumer<? super L, ? extends E> lf, ThrowingConsumer<? super R, ? extends E> rf) throws E;

    /**
     * Match on the contained value, applying the function pertaining to the contained type, and returning the result.
     * Both functions must return the same type.
     *
     * @param lf  The function to apply to the contained value if it is a {@link Left} value.
     * @param rf  The function to apply to the contained value if it is a {@link Right} value.
     * @param <T> The return type of the functions.
     * @return The value returned by the matched function.
     */
    public <T> T matchThen(Function<? super L, ? extends T> lf, Function<? super R, ? extends T> rf)
    {
        return unsafeMatch(
                lf::apply,
                rf::apply);
    }

    /**
     * Match on the contained value, performing the operation pertaining to the contained type.
     *
     * @param lf The operation to perform on the contained value if it is a {@link Left} value.
     * @param rf The operation to perform on the contained value if it is a {@link Right} value.
     */
    public void match(Consumer<? super L> lf, Consumer<? super R> rf)
    {
        unsafeMatch(
                lf::accept,
                rf::accept);
    }

    /**
     * <p>Apply a function to the contained value, and return an {@link Either} corresponding to the return types.
     * The functions need not return the same type.
     * <p>
     * <p>This does not change the enclosing structure, i.e.:
     * {@code left(x).bimap(a,b).isLeft() == true}
     * {@code right(x).bimap(a,b).isRight() == true}
     *
     * @param lf  The function to apply to the contained value if it is a {@link Left} value.
     * @param rf  The function to apply to the contained value if it is a {@link Right} value.
     * @param <A> The type of the {@link Left} function
     * @param <B> The type of the {@link Right} function
     * @return An {@link Either} containing the return value of the matched function
     */
    public <A, B> Either<A, B> bimap(Function<? super L, ? extends A> lf, Function<? super R, ? extends B> rf)
    {
        return matchThen(
                (L l) -> left(lf.apply(l)),
                (R r) -> right(rf.apply(r))
        );
    }

    /**
     * Returns the contained value if it is a {@link Left} value, otherwise returns the provided value.
     *
     * @param left The default value to use if the contained value is not {@link Left}.
     * @return the contained value if it is a {@link Left} value, otherwise the provided value.
     */
    public L fromLeft(L left)
    {
        return matchThen(
                (L l) -> l,
                (R r) -> left
        );
    }

    /**
     * Returns the contained value if it is a {@link Right} value, otherwise returns the provided value.
     *
     * @param right The default value to use if the contained value is not {@link Right}.
     * @return the contained value if it is a {@link Right} value, otherwise the provided value.
     */
    public R fromRight(R right)
    {
        return matchThen(
                (L l) -> right,
                (R r) -> r
        );
    }

    /**
     * Performs the provided operation on the contained value if it is a {@link Left} value.
     *
     * @param lf The operation to optionally perform.
     */
    public void ifLeft(Consumer<L> lf)
    {
        match(
                lf,
                (R r) -> {}
        );
    }

    /**
     * Performs the provided operation on the contained value if it is a {@link Right} value.
     *
     * @param rf The operation to optionally perform.
     */
    public void ifRight(Consumer<R> rf)
    {
        match(
                (L l) -> {},
                rf
        );
    }

    /**
     * Returns whether the contained value is {@link Left}.
     *
     * @return true if the contained value is {@link Left}.
     */
    public boolean isLeft()
    {
        return matchThen(
                (L l) -> true,
                (R r) -> false
        );
    }

    /**
     * Returns whether the contained value is {@link Right}.
     *
     * @return true if the contained value is {@link Right}.
     */
    public boolean isRight()
    {
        return matchThen(
                (L l) -> false,
                (R r) -> true
        );
    }

    /**
     * Returns an Either containing a {@link Left} value.
     *
     * @param value The value to contain
     * @param <L>   The type of the contained value
     * @param <R>   The unused type
     * @return An Either containing a {@link Left} value.
     */
    public static <L, R> Either<L, R> left(L value)
    {
        return new Left<>(value);
    }

    /**
     * Returns an Either containing a {@link Right} value.
     *
     * @param value The value to contain
     * @param <L>   The unused type
     * @param <R>   The type of the contained value
     * @return An Either containing a {@link Right} value.
     */
    public static <L, R> Either<L, R> right(R value)
    {
        return new Right<>(value);
    }

    /**
     * Returns an equivalent Either with more generic type parameters.
     *
     * @param either The Either to convert
     * @param <L>    The new Left type
     * @param <R>    The new Right type
     * @return An equivalent Either with more generic type parameters.
     */
    static <L, R> Either<L, R> cast(Either<? extends L, ? extends R> either)
    {
        return either.matchThen(
                (L l) -> left(l),
                (R r) -> right(r)
        );
    }

    /**
     * The class representing a Left value.
     *
     * @param <L> The type of the contained value
     * @param <R> The unused type
     */
    static class Left<L, R> extends Either<L, R>
    {
        /**
         * The contained value.
         */
        final L value;

        /**
         * Construct a Left value.
         *
         * @param value The value to wrap.
         */
        Left(L value)
        {
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(Left.class, value);
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Left)
            {
                Left l = (Left) o;
                return Objects.equals(value, l.value);
            }
            else
            {
                return false;
            }
        }

        @Override
        public String toString()
        {
            return "Either.left(" + value + ')';
        }

        @Override
        public <T, E extends Throwable> T unsafeMatch(ThrowingFunction<? super L, ? extends T, ? extends E> lf, ThrowingFunction<? super R, ? extends T, ? extends E> rf) throws E
        {
            return lf.apply(value);
        }

        @Override
        public <E extends Throwable> void unsafeMatch(ThrowingConsumer<? super L, ? extends E> lf, ThrowingConsumer<? super R, ? extends E> rf) throws E
        {
            lf.accept(value);
        }
    }

    /**
     * The class representing a Right value.
     *
     * @param <L> The unused type
     * @param <R> The type of the contained value
     */
    static class Right<L, R> extends Either<L, R>
    {
        /**
         * The contained value.
         */
        final R value;

        /**
         * Construct a Right value.
         *
         * @param value The value to wrap.
         */
        Right(R value)
        {
            this.value = value;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(Right.class, value);
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Right)
            {
                Right r = (Right) o;
                return Objects.equals(value, r.value);
            }
            return false;
        }

        @Override
        public String toString()
        {
            return "Either.right(" + value + ')';
        }

        @Override
        public <T, E extends Throwable> T unsafeMatch(ThrowingFunction<? super L, ? extends T, ? extends E> lf, ThrowingFunction<? super R, ? extends T, ? extends E> rf) throws E
        {
            return rf.apply(value);
        }

        @Override
        public <E extends Throwable> void unsafeMatch(ThrowingConsumer<? super L, ? extends E> lf, ThrowingConsumer<? super R, ? extends E> rf) throws E
        {
            rf.accept(value);
        }
    }
}
