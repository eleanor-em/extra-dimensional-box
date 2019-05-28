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

package unimelb.bitbox.util.functional.combinator;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class Combinators
{
    public static <A> A id(A a)
    {
        return a;
    }

    public static <A, B> A constant(A a, B __)
    {
        return a;
    }

    public static <A> void noop(A __) {}
    public static <A> void noop() {}

    public static <A, B, C> Function<A, C> compose(Function<A, B> ab, Function<B, C> bc)
    {
        return (A a) -> bc.apply(ab.apply(a));
    }

    public static <A, B, C> BiFunction<B, A, C> flip(BiFunction<A, B, C> f)
    {
        return (B b, A a) -> f.apply(a, b);
    }
}
