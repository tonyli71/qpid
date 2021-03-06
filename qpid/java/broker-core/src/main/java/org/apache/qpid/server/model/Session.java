/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.model;

import java.util.Collection;

@ManagedObject
public interface Session<X extends Session<X>> extends ConfiguredObject<X>
{
    String CHANNEL_ID = "channelId";
    // PRODUCER_FLOW_BLOCKED is exposed as an interim step.  We will expose attribute(s) that exposing
    // available credit of both producer and consumer sides.
    String PRODUCER_FLOW_BLOCKED = "producerFlowBlocked";

    @ManagedAttribute( derived = true )
    int getChannelId();

    @ManagedAttribute( derived = true )
    boolean isProducerFlowBlocked();


    Collection<Consumer> getConsumers();
    Collection<Publisher> getPublishers();

    @ManagedStatistic
    long getConsumerCount();

    @ManagedStatistic
    long getLocalTransactionBegins();

    @ManagedStatistic
    int getLocalTransactionOpen();

    @ManagedStatistic
    long getLocalTransactionRollbacks();

    @ManagedStatistic
    long getUnacknowledgedMessages();

    void delete();
}
