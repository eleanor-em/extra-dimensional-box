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
 * Represents a fallible function that accepts one argument and produces a result.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 * @param <E> the type of exceptions thrown by the function
 * @author Zoey Hewll
 */
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable>
{
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     * @throws E if the function throws an exception
     */
    R apply(T t) throws E;

    /**
     * Returns a composed function that first applies the {@code before}
     * function to its input, and then applies this function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V>    the type of input to the {@code before} function, and to the
     *               composed function
     * @param before the function to apply before this function is applied
     * @return a composed function that first applies the {@code before}
     * function and then applies this function
     * @throws NullPointerException if before is null
     * @see #andThen(ThrowingFunction)
     */
    default <V> ThrowingFunction<V, R, E> compose(ThrowingFunction<? super V, ? extends T, ? extends E> before)
    {
        Objects.requireNonNull(before);
        return (V v) -> this.apply(before.apply(v));
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V>   the type of output of the {@code after} function, and of the
     *              composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     * @see #compose(ThrowingFunction)
     */
    default <V> ThrowingFunction<T, V, E> andThen(ThrowingFunction<? super R, ? extends V, ? extends E> after)
    {
        Objects.requireNonNull(after);
        return (T t) -> after.apply(this.apply(t));
    }
}
