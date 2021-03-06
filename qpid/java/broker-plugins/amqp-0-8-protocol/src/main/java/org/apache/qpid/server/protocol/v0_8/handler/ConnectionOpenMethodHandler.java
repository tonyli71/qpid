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
package org.apache.qpid.server.protocol.v0_8.handler;

import java.security.AccessControlException;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQState;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostState;

public class ConnectionOpenMethodHandler implements StateAwareMethodListener<ConnectionOpenBody>
{
    private static final Logger _logger = Logger.getLogger(ConnectionOpenMethodHandler.class);

    private static ConnectionOpenMethodHandler _instance = new ConnectionOpenMethodHandler();

    public static ConnectionOpenMethodHandler getInstance()
    {
        return _instance;
    }

    private ConnectionOpenMethodHandler()
    {
    }

    private static AMQShortString generateClientID()
    {
        return new AMQShortString(Long.toString(System.currentTimeMillis()));
    }

    public void methodReceived(AMQStateManager stateManager, ConnectionOpenBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        //ignore leading '/'
        String virtualHostName;
        if ((body.getVirtualHost() != null) && body.getVirtualHost().charAt(0) == '/')
        {
            virtualHostName = new StringBuilder(body.getVirtualHost().subSequence(1, body.getVirtualHost().length())).toString();
        }
        else
        {
            virtualHostName = body.getVirtualHost() == null ? null : String.valueOf(body.getVirtualHost());
        }

        VirtualHostImpl virtualHost = ((AmqpPort)stateManager.getProtocolSession().getPort()).getVirtualHost(virtualHostName);

        if (virtualHost == null)
        {
            throw body.getConnectionException(AMQConstant.NOT_FOUND, "Unknown virtual host: '" + virtualHostName + "'");
        }
        else
        {
            session.setVirtualHost(virtualHost);

            // Check virtualhost access
            try
            {
                virtualHost.getSecurityManager().authoriseCreateConnection(session);
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
            }

             if (virtualHost.getVirtualHostState() != VirtualHostState.ACTIVE)
            {
                throw body.getConnectionException(AMQConstant.CONNECTION_FORCED, "Virtual host '" + virtualHost.getName() + "' is not active");
            }


            // See Spec (0.8.2). Section  3.1.2 Virtual Hosts
            if (session.getContextKey() == null)
            {
                session.setContextKey(generateClientID());
            }

            MethodRegistry methodRegistry = session.getMethodRegistry();
            AMQMethodBody responseBody =  methodRegistry.createConnectionOpenOkBody(body.getVirtualHost());

            stateManager.changeState(AMQState.CONNECTION_OPEN);

            session.writeFrame(responseBody.generateFrame(channelId));
        }
    }
}
