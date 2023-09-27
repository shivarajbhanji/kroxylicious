/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.proxy.internal.filter;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kroxylicious.proxy.filter.FilterConstructContext;
import io.kroxylicious.proxy.internal.filter.FetchResponseTransformationFilter.Factory;
import io.kroxylicious.proxy.internal.filter.FetchResponseTransformationFilter.FetchResponseTransformationConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class FetchResponseTransformationFilterTest {

    @Test
    void testContributor() {
        Factory contributor = new Factory();
        assertThat(contributor.getConfigType()).isEqualTo(FetchResponseTransformationConfig.class);
        assertThat(contributor.requiresConfiguration()).isEqualTo(true);
        FilterConstructContext constructContext = Mockito.mock(FilterConstructContext.class);
        when(constructContext.getConfig()).thenReturn(new FetchResponseTransformationConfig(ProduceRequestTransformationFilter.UpperCasing.class.getName()));
        assertThat(contributor.createInstance(constructContext)).isInstanceOf(FetchResponseTransformationFilter.class);
    }

}
