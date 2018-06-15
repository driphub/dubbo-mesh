/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.alibaba.dubbo.performance.demo.agent.dubbo.agent.consumer.server;

import com.alibaba.dubbo.performance.demo.agent.dubbo.agent.consumer.client.ConsumerAgentClient;
import com.alibaba.dubbo.performance.demo.agent.rpc.Endpoint;
import com.alibaba.dubbo.performance.demo.agent.transport.MeshChannel;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.util.AsciiString;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author 徐靖峰[OF2938]
 * company qianmi.com
 * Date 2018-05-22
 */
public class ConsumerAgentHttpServerCheatHandler extends SimpleChannelInboundHandler<String> {

    public ConsumerAgentHttpServerCheatHandler() {
    }

    private Logger logger = LoggerFactory.getLogger(ConsumerAgentHttpServerCheatHandler.class);

    public static AtomicLong requestIdGenerator = new AtomicLong(0);

//    private static AtomicInteger handlerCnt = new AtomicInteger(0);

    public static FastThreadLocal<LongObjectHashMap<Promise>> promiseHolder = new FastThreadLocal<LongObjectHashMap<Promise>>() {
        @Override
        protected LongObjectHashMap<Promise> initialValue() {
            return new LongObjectHashMap<>();
        }
    };

//    private Endpoint channelConsistenceHashEndpoint;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
//        int handlerNo = handlerCnt.incrementAndGet();
//        this.channelConsistenceHashEndpoint = ConsumerAgentHttpServer.remoteEndpoints[handlerNo % ConsumerAgentHttpServer.remoteEndpoints.length];
//        logger.info("bound channel now is {}", handlerNo);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String req) {
        processRequest(ctx, req);
    }

    private void processRequest(ChannelHandlerContext ctx, String req) {

        long requestId = requestIdGenerator.incrementAndGet();

        CompositeByteBuf agentRequest = PooledByteBufAllocator.DEFAULT.compositeBuffer();
        agentRequest
                .addComponents(true,
                        Unpooled.copyInt(8+req.length()),
                        Unpooled.copyLong(requestId),
                        Unpooled.wrappedBuffer(req.getBytes()));

        Promise<Integer> agentResponsePromise = new DefaultPromise<>(ctx.executor());
        agentResponsePromise.addListener(future -> {
            int agentResponse = (Integer) future.get();
            ctx.channel().writeAndFlush(agentResponse+"");
        });
        promiseHolder.get().put(requestId, agentResponsePromise);
        MeshChannel meshChannel = ConsumerAgentClient.get(ctx.channel().eventLoop()).getMeshChannel();
        meshChannel.getChannel().writeAndFlush(agentRequest, meshChannel.getChannel().voidPromise());
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("http服务器响应出错", cause);
        ctx.channel().close();
    }


}
