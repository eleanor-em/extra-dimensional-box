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

import org.jetbrains.annotations.Nullable;
import unimelb.bitbox.util.functional.combinator.Combinators;
import unimelb.bitbox.util.functional.combinator.Curried;
import unimelb.bitbox.util.functional.throwing.ThrowingConsumer;
import unimelb.bitbox.util.functional.throwing.ThrowingFunction;
import unimelb.bitbox.util.functional.throwing.ThrowingRunnable;
import unimelb.bitbox.util.functional.throwing.ThrowingSupplier;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>A container which may be empty, or hold a value; and provides pattern matching to safely unwrap the data.
 * <br>
 * <p>The only possible subclasses are {@link Nothing} and {@link Just}.
 * <h3>Examples:</h3>
 * In each example, an underscore (_) represents a placeholder for any valid value, where the specific value doesn't affect the output.
 * <pre> {@code
 * f(x) = y
 * g(x) = just(y);
 * h(x) = nothing();
 *
 * just(x).map(f) == just(y)
 * just(x).map(g) == just(just(y))
 * just(x).andThen(g) == just(y)
 *
 * just(x).map(h) == just(nothing())
 * just(x).andThen(h) == nothing()
 * }
 *
 * Nothing always yields Nothing:
 * {@code
 * nothing().map(_) == nothing()
 * nothing().andThen(_) == nothing()
 * }
 *
 * Matching safely unwraps the data and applies the matched operation:
 * {@code
 * just(x).match(x -> y, _) == y
 * nothing().match(_, () -> z) == z
 * } </pre>
 *
 * @param <V> The type of the optional value
 * @author Zoey Hewll
 * @author Eleanor McMurtry (renamed match overloads to avoid inference errors)
 */
public abstract class Maybe<V> implements ThrowingSupplier<V, IllegalStateException>
{
    /**
     * Effectively final, singleton instance.
     */
    private static Nothing<?> NOTHING = null;

    /**
     * Private constructor to seal the type.
     */
    private Maybe() {}

    /**
     * Decide control flow based on the structure of the Maybe,
     * returning a result or throwing a checked exception.
     *
     * @see #match
     *
     * @param some the operation to perform on the contained value if there is one
     * @param none the operation to perform if there is no contained value
     * @param <T>  the type of the value returned by the supplied operations
     * @param <E>  the exception type which may be thrown by one or both operations
     * @return the result of the matched operation
     * @throws E the exception thrown by the supplied operations
     */
    public abstract <T, E extends Exception> T unsafeMatchThen(ThrowingFunction<? super V, ? extends T, ? extends E> some, ThrowingSupplier<? extends T, ? extends E> none) throws E;

    /**
     * Decide control flow based on the structure of the Maybe,
     * optionally throwing a checked exception.
     *
     * @see #match
     *
     * @param some the operation to perform on the contained value if there is one
     * @param none the operation to perform if there is no contained value
     * @param <E>  the exception type which may be thrown by one or both operations
     * @throws E the exception thrown by the supplied operations
     */
    public abstract <E extends Exception> void unsafeMatch(ThrowingConsumer<? super V, ? extends E> some, ThrowingRunnable<? extends E> none) throws E;

    /**
     * Decide control flow by opening the structure of the Maybe,
     * returning the result of the chosen branch.
     *
     * @param some the operation to perform on the contained value if there is one
     * @param none the operation to perform if there is no contained value
     * @param <T>  the type of the value returned by the supplied operations
     * @return the result of the matched operation
     */
    public <T> T matchThen(Function<? super V, ? extends T> some, Supplier<? extends T> none)
    {
        return unsafeMatchThen(
                some::apply,
                none::get);
    }

    /**
     * Decide control flow based on the structure of the Maybe.
     *
     * @param some the operation to perform on the contained value if there is one
     * @param none the operation to perform if there is no contained value
     */
    public void match(Consumer<? super V> some, Runnable none)
    {
        unsafeMatch(
                some::accept,
                none::run);
    }

    /**
     * Applies the mapping function if there is a contained value, returning the result in a Maybe.
     * This is guaranteed to produce a Maybe of the same structure.
     *
     * @param f   the function to apply to the contained value
     * @param <T> the type of the function's return value
     * @return the modified Maybe
     */
    public abstract <T> Maybe<T> map(Function<? super V, ? extends T> f);

    /**
     * Performs the operation on any contained value and returns the resulting Maybe if there is one.
     * This can produce a Maybe of a different structure.
     *
     * @param f   the function to apply to the contained value
     * @param <T> the type of the function's optional return value
     * @return the modified Maybe
     */
    public abstract <T> Maybe<T> andThen(Function<? super V, ? extends Maybe<? extends T>> f);

    /**
     * Returns the contained value, if it exists, otherwise raise an exception.
     *
     * @return The contained value, if it exists
     * @throws IllegalStateException if nothing is contained
     */
    public abstract V get() throws IllegalStateException;
    public Maybe<V> setIfNothing(V value) {
        return Maybe.just(this.fromMaybe(value));
    }

    /**
     * Returns the contained value, or the supplied default if it does not exist.
     *
     * @param value the default value to use
     * @return the contained value, or the supplied default if it does not exist
     */
    public V fromMaybe(V value)
    {
        return matchThen(
                (V v) -> v,
                () -> value
        );
    }

    /**
     * Returns the contained value, or the supplied default if it does not exist.
     *
     * @param supplier the supplier of the value to use
     * @return the contained value, or the supplied default if it does not exist
     */
    public V fromMaybe(Supplier<V> supplier)
    {
        return matchThen(Combinators::id, supplier);
    }

    /**
     * Returns true if there is a contained value.
     *
     * @return true if there is a contained value
     */
    public boolean isJust()
    {
        return matchThen(
                (V v) -> true,
                () -> false
        );
    }

    public void consume(Consumer<V> consumer) {
        match(consumer, Combinators::noop);
    }

    /**
     * Converts a nullable value into an equivalent Maybe, returning Nothing if the value is null, and just(value) otherwise.
     *
     * @param value the value to wrap
     * @return nothing() if the value is null, and just(value) otherwise
     * @param <V> The type of the optional value
     */
    public static <V> Maybe<V> of(@Nullable V value)
    {
        return value == null
                ? nothing()
                : just(value);
    }

    /**
     * Turns an Optional into a Maybe, turning Empty into Nothing, and a present value into just(value).
     *
     * @param value the optional to transform
     * @return A Maybe mirroring the Optional parameter
     * @param <V> The type of the optional value
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static <V> Maybe<V> of(Optional<V> value)
    {
        return value.map(Maybe::just).orElse(nothing());
    }

    /**
     * Wraps the value in a Maybe.
     *
     * @param value The value to wrap
     * @param <V>   The type of the containted value
     * @return The value wrapped in a Maybe
     */
    public static <V> Maybe<V> just(V value)
    {
        return new Just<>(value);
    }

    /**
     * Returns the singleton Nothing instance.
     *
     * @param <V> The type of the non-existent contained value
     * @return The singleton Nothing instance
     */
    public static <V> Maybe<V> nothing()
    {
        if (NOTHING == null)
        {
            NOTHING = new Nothing<>();
        }
        @SuppressWarnings("unchecked") Nothing<V> nothing = (Nothing<V>) NOTHING;
        return nothing;
    }

    /**
     * Upcast the container by upcasting the contained type.
     *
     * @param m   The Maybe to cast
     * @param <T> The type to cast to
     * @param <V> The type to cast from
     * @return A Maybe of the upcast type
     */
    static <T, V extends T> Maybe<T> cast(Maybe<V> m)
    {
        return m.matchThen(Maybe::just, Maybe::nothing);
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public abstract String toString();

    public V orElse(V b) {
        return matchThen(
                Combinators::id,
                Curried.id(b)
        );
    }

    public void ok(Consumer<V> consumer) {
        match(consumer, Combinators::noop);
    }
    public <E extends Exception> void okT(ThrowingConsumer<V, E> consumer) throws E {
        if (isJust()) {
            consumer.accept(get());
        }
    }

    /**
     * The class representing an absent value.
     *
     * @param <V> unused
     */
    private static class Nothing<V> extends Maybe<V>
    {
        @Override
        public boolean equals(Object o)
        {
            return o instanceof Nothing;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(Nothing.class);
        }

        @Override
        public String toString()
        {
            return "Maybe.nothing()";
        }

        @Override
        public <T, E extends Exception> T unsafeMatchThen(ThrowingFunction<? super V, ? extends T, ? extends E> some, ThrowingSupplier<? extends T, ? extends E> none) throws E
        {
            return none.get();
        }

        @Override
        public <E extends Exception> void unsafeMatch(ThrowingConsumer<? super V, ? extends E> some, ThrowingRunnable<? extends E> none) throws E
        {
            none.run();
        }

        @Override
        public <T> Maybe<T> map(Function<? super V, ? extends T> f)
        {
            return nothing();
        }

        @Override
        public <T> Maybe<T> andThen(Function<? super V, ? extends Maybe<? extends T>> f)
        {
            return nothing();
        }

        @Override
        public V get() throws IllegalStateException
        {
            throw new IllegalStateException();
        }
    }

    /**
     * The class representing a present value.
     *
     * @param <V> The type of the contained value.
     */
    private static class Just<V> extends Maybe<V>
    {
        /**
         * The contained value.
         */
        final V value;

        /**
         * Construct a Maybe with the provided value.
         *
         * @param value The value to wrap.
         */
        Just(V value)
        {
            this.value = value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (o instanceof Just)
            {
                Just j = (Just) o;
                return Objects.equals(value, j.value);
            }
            return false;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(Just.class, value);
        }

        @Override
        public String toString()
        {
            return "Maybe.just(" + value + ')';
        }

        @Override
        public <T, E extends Exception> T unsafeMatchThen(ThrowingFunction<? super V, ? extends T, ? extends E> some, ThrowingSupplier<? extends T, ? extends E> none) throws E
        {
            return some.apply(value);
        }

        @Override
        public <E extends Exception> void unsafeMatch(ThrowingConsumer<? super V, ? extends E> some, ThrowingRunnable<? extends E> none) throws E
        {
            some.accept(value);
        }

        @Override
        public <T> Maybe<T> map(Function<? super V, ? extends T> f)
        {
            return just(f.apply(value));
        }

        @Override
        public <T> Maybe<T> andThen(Function<? super V, ? extends Maybe<? extends T>> f)
        {
            return cast(f.apply(value));
        }

        @Override
        public V get()
        {
            return value;
        }
    }
}
