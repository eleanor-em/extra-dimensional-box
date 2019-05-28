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

import unimelb.bitbox.util.functional.algebraic.utils.Voids;
import unimelb.bitbox.util.functional.combinator.Combinators;
import unimelb.bitbox.util.functional.throwing.ThrowingConsumer;
import unimelb.bitbox.util.functional.throwing.ThrowingFunction;
import unimelb.bitbox.util.functional.throwing.ThrowingRunnable;
import unimelb.bitbox.util.functional.throwing.ThrowingSupplier;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Result is a container representing the result of an operation that may throw an exception;
 * It provides pattern matching to handle control flow when extracting the data,
 * and operations to mutate or sequence results.
 * This allows checked exceptions to be encapsulated in functional code,
 * and exception handling in the form of pattern matching.
 *
 * @param <E> the error alternative type.
 * @param <V> the value alternative type.
 * @author Zoey Hewll
 */
public class Result<E extends Exception, V> implements ThrowingSupplier<V, E>
{
    /**
     * The Either type used internally to hold the alternative values.
     */
    private Either<E, V> either;

    /**
     * Constructor.
     * Makes a Result from the given Either.
     *
     * @param either The Either value used to maintain internal structure.
     */
    private Result(Either<E, V> either)
    {
        this.either = either;
    }

    /**
     * Perform an operation which may throw a checked exception, and encapsulate the result.<br>
     * If the operation succeeds, the Result will contain the returned value.<br>
     * If the operation fails, the Result will contain the thrown checked exception.
     * <p>
     * <p>Conceptually the inverse of {@link #get}.
     * {@code r <==> of(r::get)}<br>
     * {@code f.get() <==> of(f).get()}<br>
     *
     * @param v   the operation to perform
     * @param <E> the type of checked exception which may be thrown
     * @param <V> the type of value which may be returned
     * @return A Result representing the outcome of the operation.
     */
    public static <E extends Exception, V> Result<E, V> of(ThrowingSupplier<? extends V, ? extends E> v)
    {
        try
        {
            return value(v.get());
        }
        catch (RuntimeException e)
        {
            // This ensures that only checked exceptions are caught by the following clause.
            throw e;
        }
        catch (Exception e)
        {
            @SuppressWarnings("unchecked") final E ex = (E) e;
            return error(ex);
        }
    }

    /**
     * Perform an operation which may throw a checked exception, and encapsulate the result.
     * If the operation succeeds, the Result will contain nothing (Void).
     * If the operation fails, the Result will contain the thrown checked exception.
     * <p>
     * <p>Conceptually the inverse of {@link #get}.
     * {@code r <==> of(r::get)}<br>
     * {@code f.run() <~~> of(f).get()}<br>
     *
     * @param v   the operation to perform
     * @param <E> the type of checked exception which may be thrown
     * @return A Result representing the outcome of the operation.
     */
    public static <E extends Exception> Result<E, Void> of(ThrowingRunnable<? extends E> v)
    {
        return of(Voids.convertUnsafe(v));
    }

    /**
     * Perform an operation which may throw a runtime exception, and encapsulate the result.
     * If the operation succeeds, the Result will contain the returned value.
     * If the operation fails, the Result will contain the thrown runtime exception.
     * <p>
     * <p>Conceptually the inverse of {@link #get}.
     * {@code r <==> ofRuntime(r.get())}<br>
     * {@code f.get() <==> ofRuntime(f).get()}<br>
     *
     * @param v   the operation to perform
     * @param <V> the type of value which may be returned
     * @return A Result representing the outcome of the operation.
     */
    public static <V> Result<RuntimeException, V> ofRuntime(Supplier<? extends V> v)
    {
        try
        {
            return value(v.get());
        }
        catch (RuntimeException e)
        {
            return error(e);
        }
    }

    /**
     * Perform an operation which may throw a runtime exception, and encapsulate the result.
     * If the operation succeeds, the Result will contain nothing (Void).
     * If the operation fails, the Result will contain the thrown runtime exception.
     * <p>
     * <p>Conceptually the inverse of {@link #get}.
     * {@code r <==> ofRuntime(r.get())}<br>
     * {@code f() <==> ofRuntime(f).get()}<br>
     *
     * @param v the operation to perform
     * @return A Result representing the outcome of the operation.
     */
    public static Result<RuntimeException, Void> ofRuntime(Runnable v)
    {
        return ofRuntime(Voids.convert(v));
    }

    /**
     * Convert an operation that may throw a checked exception into an operation that returns a result.
     *
     * @param v   The operation to convert
     * @param <E> The checked exception type
     * @param <V> The operation's return type
     * @return The converted operation, which returns a result instead of throwing an exception.
     */
    public static <E extends Exception, V> Supplier<Result<E, V>> convert(ThrowingSupplier<? extends V, ? extends E> v)
    {
        return () -> of(v);
    }

    /**
     * Convert an action that may throw a checked exception into an operation that returns a result.
     *
     * @param v   The action to convert
     * @param <E> The checked exception type
     * @return The converted operation, which returns a result instead of throwing an exception.
     */
    public static <E extends Exception> Supplier<Result<E, Void>> convert(ThrowingRunnable<? extends E> v)
    {
        return () -> of(v);
    }

    /**
     * Convert a function that may throw a checked exception into a function that returns a result.
     *
     * @param v   The function to convert
     * @param <T> The function's parameter type
     * @param <V> The function's return type
     * @param <E> The checked exception type
     * @return The converted function, which returns a result instead of throwing an exception.
     */
    public static <T, E extends Exception, V> Function<T, Result<E, V>> convert(ThrowingFunction<? super T, ? extends V, ? extends E> v)
    {
        return (T t) -> of(() -> v.apply(t));
    }

    /**
     * Convert an operation that may throw a checked exception into a function that returns a result.
     *
     * @param v   The operation to convert
     * @param <T> The consumer's parameter type
     * @param <E> The checked exception type
     * @return The converted function, which returns a result instead of throwing an exception.
     */
    public static <T, E extends Exception> Function<T, Result<E, Void>> convert(ThrowingConsumer<? super T, ? extends E> v)
    {
        return (T t) -> of(() -> v.accept(t));
    }

    /**
     * Converts a nested Result (aka Result of a Result) into a single Result.
     *
     * @param r   The result to join
     * @param <E> the error alternative type.
     * @param <V> the value alternative type.
     * @return A unified Result
     */
    static <E extends Exception, V> Result<E, V> join(Result<? extends E, ? extends Result<? extends E, ? extends V>> r)
    {
        return cast(r.matchThen(
                e -> error(e),
                v -> v
        ));
    }

    /**
     * Returns a Result containing the provided exception.
     *
     * @param e   The exception to contain
     * @param <E> The type of the contained exception
     * @param <V> The unused value type
     * @return A Result containing the provided exception.
     */
    public static <E extends Exception, V> Result<E, V> error(E e)
    {
        return new Result<>(Either.left(e));
    }

    /**
     * Returns a Result containing the provided value.
     *
     * @param v   The value to contain
     * @param <E> The unused error type
     * @param <V> The type of the contained value
     * @return A Result containing the provided exception.
     */
    public static <E extends Exception, V> Result<E, V> value(V v)
    {
        return new Result<>(Either.right(v));
    }

    /**
     * Returns an equivalent Result with more generic type parameters.
     *
     * @param r   The Result to convert
     * @param <E> The new error type
     * @param <V> The new value type
     * @return An equivalent Either with more generic type parameters.
     */
    static <E extends Exception, V> Result<E, V> cast(Result<? extends E, ? extends V> r)
    {
        return new Result<>(Either.cast(r.either));
    }

    /**
     * Match on the result, applying the function pertaining to the contained type, and returning the result.
     * Both functions must return the same type.
     *
     * @param ef  The function to apply to the result if it is an {@link #error}.
     * @param vf  The function to apply to the result if it is a {@link #value}.
     * @param <T> The return type of the functions.
     * @return The value returned by the matched function.
     */
    public <T> T matchThen(Function<? super E, ? extends T> ef, Function<? super V, ? extends T> vf)
    {
        return either.matchThen(ef, vf);
    }

    /**
     * Match on the result, performing the operation pertaining to the contained type.
     *
     * @param ef The operation to perform if the result is an {@link #error}.
     * @param vf The operation to perform if the result is a {@link #value}.
     */
    public void match(Consumer<? super E> ef, Consumer<? super V> vf)
    {
        either.match(ef, vf);
    }

    public void collapse(Consumer<Object> f) {
        either.match(f, f);
    }

    /**
     * Returns the value in this Result, or a default if there is an error.
     * @param defaultValue
     * @return
     */
    public V orElse(V defaultValue) {
        return either.matchThen(ignored -> defaultValue, val -> val);
    }

    /**
     * Produce a Result of another type from the value in this result, or propagate the error.
     * Equivalent to haskell's Monadic bind {@code (this >>=)}.
     *
     * @param f the function to bind
     * @param <T> The function's return type
     * @return the result of the bound computation
     */
    public <T> Result<E, T> andThen(Function<? super V, ? extends Result<? extends E, ? extends T>> f)
    {
        return join(map(f));
    }

    /**
     * Equivalent to haskell's Monadic bind {@code (this >>=)}, applied to a throwing function.
     *
     * @param f the throwing function to bind
     * @param <T> The function's return type
     * @return the result of the bound computation
     */
    public <T> Result<E, T> andThenT(ThrowingFunction<? super V, ? extends T, ? extends E> f)
    {
        return andThen(convert(f));
    }

    /**
     * Equivalent to haskell's Monadic bind {@code (this >>=)}, applied to a throwing consumer.
     *
     * @param f
     * @return
     */
    public Result<E, Void> andThenT(ThrowingConsumer<? super V, ? extends E> f)
    {
        return andThen(convert(f));
    }

    /**
     * Produce a new Result, ignoring the value in this one and propagating the error.
     * Equivalent to haskell {@code (this *>)}.
     *
     * @param f
     * @return
     * @param <T> The supplier's return type
     */
    public <T> Result<E, T> and(Supplier<? extends Result<? extends E, ? extends T>> f)
    {
        return andThen((__) -> f.get());
    }

    /**
     * Equivalent to haskell {@code (this *>)}.
     *
     * @param f
     * @return
     * @param <T> The supplier's return type
     */
    public <T> Result<E, T> andT(ThrowingSupplier<? extends T, ? extends E> f)
    {
        return and(convert(f));
    }

    /**
     * Perform the given action and use its Result's value.
     * returning {@code this} if there was no error, otherwise the resulting error.
     * <p>
     * Equivalent to haskell {@code (this *>)}
     *
     * @param f
     * @return
     */
    public Result<E, Void> andT(ThrowingRunnable<? extends E> f)
    {
        return and(convert(f));
    }

    /**
     * Equivalent to haskell {@code (this <*)}.
     *
     * @param r
     * @return
     * @param <F> The supplier's error type
     */
    public <F extends Exception> Result<F, V> or(Supplier<? extends Result<? extends F, ? extends V>> r)
    {
        return matchThen(
                (E e) -> cast(r.get()),
                (V v) -> value(v)
        );
    }

    public <F extends Exception> Result<F, V> or(Result<? extends F, ? extends V> r) {
        return or(() -> r);
    }

    /**
     * Equivalent to haskell {@code (this <*)}.
     *
     * @param f
     * @return
     * @param <F> The supplier's error type
     */
    public <F extends Exception> Result<F, V> orT(ThrowingSupplier<? extends V, ? extends F> f)
    {
        return or(convert(f));
    }

    /**
     * Perform an operation on the value inside this result, or do nothing if there is an error.
     * @param op
     * @return
     */
    public Result<E, V> ok(Consumer<? super V> op) {
        match(Combinators::noop, op);
        return this;
    }


    /**
     * Apply the given function to the contained value and discard the Result's value,
     * returning {@code this} if there was no error, otherwise the resulting error.
     * <p>
     * Equivalent to haskell {@code (this <*) . (this >>=)}
     *
     * @param f the function to apply
     * @return
     */
    public Result<E, V> peek(Function<? super V, ? extends Result<? extends E, ?>> f)
    {
        return andThen((V v) -> f.apply(v).set(v));
    }

    /**
     * Apply the given function to the contained value and discard the returned value,
     * returning {@code this} if there was no error, otherwise the resulting error.
     *
     * @param f the function to apply
     * @return
     */
    public Result<E, V> peekT(ThrowingFunction<? super V, ?, ? extends E> f)
    {
        return peek(convert(f));
    }

    /**
     * Apply the given consumer to the contained value and discard the returned value,
     * returning {@code this} if there was no error, otherwise the resulting error.

     * @param f the consumer to apply
     * @return
     */
    public Result<E, V> peekT(ThrowingConsumer<? super V, ? extends E> f)
    {
        return peek(convert(f));
    }

    /**
     * Equivalent to haskell {@code (this *>)}
     *
     * @param value
     * @return
     * @param <T> The result's value type
     */
    public <T> Result<E, T> and(Result<? extends E, ? extends T> value)
    {
        return and(() -> value);
    }

    /**
     * Equivalent to haskell {@code (this *>) . pure}
     *
     * @param value
     * @return
     * @param <T> The value type
     */
    public <T> Result<E, T> set(T value)
    {
        return and(value(value));
    }

    /**
     * Apply the function to the contained value, if it is present, and return the modified Result.
     *
     * @param f   The function to apply to the contained value
     * @param <T> The return type of the function
     * @return The modified Result.
     */
    public <T> Result<E, T> map(Function<? super V, ? extends T> f)
    {
        return matchThen(
                (E e) -> error(e),
                (V v) -> value(f.apply(v))
        );
    }

    public <T extends Exception> Result<T, V> mapError(Function<? super E, ? extends T> f) {
        return matchThen(
                (E e) -> error(f.apply(e)),
                (V v) -> value(v)
        );
    }

    public V consumeError(Function<? super E, ? extends V> f) {
        return matchThen(f, Combinators::id);
    }

    /**
     * Apply the function to the contained value and the supplied value, if present, and return the modified Result.
     *
     * @param f   The bifunction to apply to the contained value
     * @return The modified Result.
     */
    public <U,R> Result<E, R> bimap(Result<E, U> r, BiFunction<? super V, ? super U, ? extends R> f)
    {
        return andThen(
                (V v) -> r.andThen(
                        (U u) -> value(
                                f.apply(v, u))));
    }

    /**
     * If the result is a value, return it.<br>
     * If it is an exception, throw it.<br>
     *
     * <p>Conceptually the inverse of {@link #of} and {@link #ofRuntime}.
     * {@code r <==> of(r.get())}<br>
     * {@code f() <==> of(f()).get()}<br>
     *
     * @return the contained value
     * @throws E the contained exception
     */
    public V get() throws E
    {
        return either.unsafeMatch(
                (E e) -> {throw e;},
                (V v) -> v
        );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(Result.class, either.hashCode());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Result)
        {
            Result r = (Result) o;
            return either.equals(r.either);
        }
        return false;
    }

    public String toString()
    {
        return matchThen(
                e -> "Result.error(" + e + ')',
                v -> "Result.value(" + v + ')'
        );
    }

}
