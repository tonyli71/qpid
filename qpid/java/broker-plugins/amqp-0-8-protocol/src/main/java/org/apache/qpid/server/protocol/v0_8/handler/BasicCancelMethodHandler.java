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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;

public class BasicCancelMethodHandler implements StateAwareMethodListener<BasicCancelBody>
{
    private static final Logger _log = Logger.getLogger(BasicCancelMethodHandler.class);

    private static final BasicCancelMethodHandler _instance = new BasicCancelMethodHandler();

    public static BasicCancelMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicCancelMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, BasicCancelBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        final AMQChannel channel = session.getChannel(channelId);


        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        if (_log.isDebugEnabled())
        {
            _log.debug("BasicCancel: for:" + body.getConsumerTag() +
                       " nowait:" + body.getNowait());
        }

        channel.unsubscribeConsumer(body.getConsumerTag());
        if (!body.getNowait())
        {
            MethodRegistry methodRegistry = session.getMethodRegistry();
            BasicCancelOkBody cancelOkBody = methodRegistry.createBasicCancelOkBody(body.getConsumerTag());
            channel.sync();
            session.writeFrame(cancelOkBody.generateFrame(channelId));
        }
    }
}
