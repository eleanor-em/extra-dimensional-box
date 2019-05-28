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

/**
 * Represents a fallible operation that accepts no input and returns no result.
 * Unlike most other functional interfaces, {@code ThrowingRunnable} is expected
 * to operate via side-effects.
 *
 * @param <E> the type of exceptions thrown by the operation
 * @author Zoey Hewll
 */
@FunctionalInterface
public interface ThrowingRunnable<E extends Throwable>
{
    /**
     * Perform this operation.
     *
     * @throws E it the operation throws an exception
     */
    void run() throws E;

    /**
     * Returns a composed supplier that first performs this operation,
     * and then returns the value supplied by {@code after}.
     * If evaluation of either operation throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <R>   the type of output of {@code after}, and of the composed function
     * @param after the supplier to use after this operation
     * @return a composed {@code ThrowingSupplier} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default <R> ThrowingSupplier<R, E> andThen(ThrowingSupplier<R, E> after)
    {
        return () ->
        {
            run();
            return after.get();
        };
    }
}
