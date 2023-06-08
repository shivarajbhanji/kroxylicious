/*
 * Copyright Kroxylicious Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.kroxylicious.filters;

import org.apache.kafka.common.message.ApiVersionsRequestData;
import org.apache.kafka.common.message.ApiVersionsResponseData;
import org.apache.kafka.common.message.ProduceRequestData;
import org.apache.kafka.common.message.ProduceResponseData;
import org.apache.kafka.common.message.RequestHeaderData;
import org.apache.kafka.common.message.ResponseHeaderData;
import org.openjdk.jmh.infra.Blackhole;

import io.kroxylicious.proxy.filter.ApiVersionsRequestFilter;
import io.kroxylicious.proxy.filter.ApiVersionsResponseFilter;
import io.kroxylicious.proxy.filter.KrpcFilterContext;
import io.kroxylicious.proxy.filter.ProduceRequestFilter;
import io.kroxylicious.proxy.filter.ProduceResponseFilter;

import static io.kroxylicious.benchmarks.InvokerDispatchBenchmark.CONSUME_TOKENS;

public class FourInterfaceFilter0 implements ProduceResponseFilter, ProduceRequestFilter, ApiVersionsRequestFilter, ApiVersionsResponseFilter {

    @Override
    public void onProduceRequest(short apiVersion, RequestHeaderData header, ProduceRequestData request, KrpcFilterContext context) {
        Blackhole.consumeCPU(CONSUME_TOKENS);
    }

    @Override
    public void onProduceResponse(short apiVersion, ResponseHeaderData header, ProduceResponseData response, KrpcFilterContext context) {
        Blackhole.consumeCPU(CONSUME_TOKENS);
    }

    @Override
    public void onApiVersionsRequest(short apiVersion, RequestHeaderData header, ApiVersionsRequestData request, KrpcFilterContext context) {
        Blackhole.consumeCPU(CONSUME_TOKENS);
    }

    @Override
    public void onApiVersionsResponse(short apiVersion, ResponseHeaderData header, ApiVersionsResponseData response, KrpcFilterContext context) {
        Blackhole.consumeCPU(CONSUME_TOKENS);
    }
}