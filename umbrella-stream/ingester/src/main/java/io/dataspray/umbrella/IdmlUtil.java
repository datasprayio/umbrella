/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.umbrella;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.idml.IdmlValue;
import scala.Option;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Function;

public class IdmlUtil {

    public <T> ImmutableMap<String, T> parseMap(IdmlValue input, Function<IdmlValue, T> keyMapper) {
        ImmutableMap.Builder<String, T> builder = ImmutableMap.builder();
        input.keys().iterator().foreach(key -> {
            builder.put(key.toStringValue(), keyMapper.apply(input.get(key)));
            return null;
        });
        return builder.build();
    }

    public <T> ImmutableList<T> parseList(IdmlValue input, Function<IdmlValue, T> objMapper) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        input.iterator().foreach(obj -> {
            builder.add(objMapper.apply(input.get(obj)));
            return null;
        });
        return builder.build();
    }

    @Nullable
    public <T> T optionOrNull(Option<?> scalaOption) {
        if (scalaOption == null) {
            return null;
        }
        if (scalaOption.isEmpty()) {
            return null;
        }
        //noinspection unchecked Scala fails to propagate the type
        return (T) scalaOption.get();
    }

    public <T> Optional<T> optionToOptional(Option<?> scalaOption) {
        if (scalaOption == null) {
            return Optional.empty();
        }
        if (scalaOption.isEmpty()) {
            return Optional.empty();
        }
        //noinspection unchecked Scala fails to propagate the type
        return Optional.of((T) scalaOption.get());
    }
}
