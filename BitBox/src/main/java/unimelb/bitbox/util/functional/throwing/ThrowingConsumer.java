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

package unimelb.bitbox.util.functional.throwing;

import java.util.Objects;

/**
 * Represents a fallible operation that accepts a single input argument and returns no
 * result. Unlike most other functional interfaces, {@code ThrowingConsumer} is expected
 * to operate via side-effects.
 *
 * @param <T> the type of the input to the operation
 * @param <E> the type of exceptions thrown by the operation
 * @author Zoey Hewll
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable>
{
    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     * @throws E if the operation throws an exception
     */
    void accept(T t) throws E;

    /**
     * Returns a composed {@code ThrowingConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code ThrowingConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default ThrowingConsumer<T, E> andThen(ThrowingConsumer<? super T, ? extends E> after)
    {
        Objects.requireNonNull(after);
        return (T t) ->
        {
            this.accept(t);
            after.accept(t);
        };
    }

    /**
     * Returns a composed function that first performs this operation on
     * its input, and then returns the value supplied by {@code after}.
     * If evaluation of either operation throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <R>   the type of output of {@code after}, and of the composed function
     * @param after the supplier to use after this operation
     * @return a composed {@code ThrowingFunction} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default <R> ThrowingFunction<T, R, E> andThen(ThrowingSupplier<R, E> after)
    {
        return (T t) ->
        {
            this.accept(t);
            return after.get();
        };
    }
}
