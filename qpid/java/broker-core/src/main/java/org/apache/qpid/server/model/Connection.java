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

@ManagedObject( creatable = false )
public interface Connection<X extends Connection<X>> extends ConfiguredObject<X>
{

    // Attributes

    String STATE = "state";

    String CLIENT_ID = "clientId";
    String CLIENT_VERSION = "clientVersion";
    String INCOMING = "incoming";
    String LOCAL_ADDRESS = "localAddress";
    String PRINCIPAL = "principal";
    String PROPERTIES = "properties";
    String REMOTE_ADDRESS = "remoteAddress";
    String REMOTE_PROCESS_NAME = "remoteProcessName";
    String REMOTE_PROCESS_PID = "remoteProcessPid";
    String SESSION_COUNT_LIMIT = "sessionCountLimit";
    String TRANSPORT = "transport";
    String PORT = "port";

    @ManagedAttribute( derived = true )
    String getClientId();

    @ManagedAttribute( derived = true )
    String getClientVersion();

    @ManagedAttribute( derived = true )
    boolean isIncoming();

    @ManagedAttribute( derived = true )
    String getLocalAddress();

    @ManagedAttribute( derived = true )
    String getPrincipal();

    @ManagedAttribute( derived = true )
    String getRemoteAddress();

    @ManagedAttribute( derived = true )
    String getRemoteProcessName();

    @ManagedAttribute( derived = true )
    String getRemoteProcessPid();

    @ManagedAttribute( derived = true )
    long getSessionCountLimit();

    @ManagedAttribute( derived = true )
    Transport getTransport();

    @ManagedAttribute( derived = true )
    Port getPort();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();

    @ManagedStatistic
    long getLastIoTime();

    @ManagedStatistic
    int getSessionCount();

    //children
    Collection<Session> getSessions();

    void delete();


}
