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
import java.util.function.Supplier;

public final class Curried
{
    public static <A> Supplier<A> id(A a)
    {
        return () -> a;
    }

    public static <A, B> Function<B, A> constant(A a)
    {
        return Curried.<A, B, A>curry(Combinators::<A,B>constant).apply(a);
    }

    public static <A, B, C> Function<Function<B, C>, Function<A, C>> compose(Function<A, B> ab)
    {
        return Curried.<Function<A, B>, Function<B, C>, Function<A, C>>curry(Combinators::<A,B,C>compose).apply(ab);
    }

    public static <A, B, C> Function<B, Function<A, C>> flip(BiFunction<A, B, C> f)
    {
        return curry(Combinators.flip(f));
    }

    public static <A, B, C> Function<B, Function<A, C>> flip(Function<A, Function<B, C>> f)
    {
        return (B b) -> (A a) -> f.apply(a).apply(b);
    }

    public static <A, B, C> Function<A, Function<B, C>> curry(BiFunction<A, B, C> f)
    {
        return (A a) -> (B b) -> f.apply(a, b);
    }

    public static <A, B, C> Function<B, C> curryWith(BiFunction<A, B, C> f, A a)
    {
        return (B b) -> f.apply(a, b);
    }

    public static <A, B, C> BiFunction<A, B, C> uncurry(Function<A, Function<B, C>> f)
    {
        return (A a, B b) -> f.apply(a).apply(b);
    }
}
