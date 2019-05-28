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
 * Represents a fallible supplier of results.
 * <p>
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * @param <T> the type of results supplied by this supplier
 * @param <E> the type of exceptions thrown by this supplier
 * @author Zoey Hewll
 */
@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable>
{
    /**
     * Gets a result.
     *
     * @return a result
     * @throws E if the operation throws an exception
     */
    T get() throws E;
}
