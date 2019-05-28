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

package unimelb.bitbox.util.functional.algebraic.utils;

import unimelb.bitbox.util.functional.throwing.ThrowingConsumer;
import unimelb.bitbox.util.functional.throwing.ThrowingFunction;
import unimelb.bitbox.util.functional.throwing.ThrowingRunnable;
import unimelb.bitbox.util.functional.throwing.ThrowingSupplier;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A utility class for converting between {@link Void} and {@code void}
 * @author Zoey Hewll
 */
public final class Voids
{
    /**
     * Turns a function returning void into a function returning Void.
     *
     * @param f   The function to convert
     * @param <E> The parameter type of the function
     * @return An equivalent function that returns Void instead of void
     */
    public static <E> Function<E, Void> convert(Consumer<E> f)
    {
        return Voids.<E, RuntimeException>convertUnsafe(f::accept)::apply;
    }

    /**
     * Turns a function returning void into a function returning Void.
     *
     * @param f The function to convert
     * @return An equivalent function that returns Void instead of void
     */
    public static Supplier<Void> convert(Runnable f)
    {
        return Voids.<RuntimeException>convertUnsafe(f::run)::get;
    }

    /**
     * Turns a function returning void into a function returning Void.
     *
     * @param f   The function to convert
     * @param <T> The parameter type of the function
     * @return An equivalent function that returns Void instead of void
     */
    public static <T, E extends Exception> ThrowingFunction<T, Void, E> convertUnsafe(ThrowingConsumer<T, E> f)
    {
        return f.andThen(() -> null);
    }

    /**
     * Turns a function returning void into a function returning Void.
     *
     * @param f The function to convert
     * @return An equivalent function that returns Void instead of void
     */
    public static <E extends Exception> ThrowingSupplier<Void, E> convertUnsafe(ThrowingRunnable<E> f)
    {
        return f.andThen(() -> null);
    }
}
