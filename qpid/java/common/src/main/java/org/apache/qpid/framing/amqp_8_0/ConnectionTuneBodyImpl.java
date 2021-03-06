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

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
 *   8-0
 */

package org.apache.qpid.framing.amqp_8_0;

import org.apache.qpid.codec.MarkableDataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.framing.*;
import org.apache.qpid.AMQException;

public class ConnectionTuneBodyImpl extends AMQMethodBody_8_0 implements ConnectionTuneBody
{
    private static final AMQMethodBodyInstanceFactory FACTORY_INSTANCE = new AMQMethodBodyInstanceFactory()
    {
        public AMQMethodBody newInstance(MarkableDataInput in, long size) throws AMQFrameDecodingException, IOException
        {
            return new ConnectionTuneBodyImpl(in);
        }
    };

    public static AMQMethodBodyInstanceFactory getFactory()
    {
        return FACTORY_INSTANCE;
    }

    public static final int CLASS_ID =  10;
    public static final int METHOD_ID = 30;

    // Fields declared in specification
    private final int _channelMax; // [channelMax]
    private final long _frameMax; // [frameMax]
    private final int _heartbeat; // [heartbeat]

    // Constructor
    public ConnectionTuneBodyImpl(MarkableDataInput buffer) throws AMQFrameDecodingException, IOException
    {
        _channelMax = readUnsignedShort( buffer );
        _frameMax = readUnsignedInteger( buffer );
        _heartbeat = readUnsignedShort( buffer );
    }

    public ConnectionTuneBodyImpl(
                                int channelMax,
                                long frameMax,
                                int heartbeat
                            )
    {
        _channelMax = channelMax;
        _frameMax = frameMax;
        _heartbeat = heartbeat;
    }

    public int getClazz()
    {
        return CLASS_ID;
    }

    public int getMethod()
    {
        return METHOD_ID;
    }

    public final int getChannelMax()
    {
        return _channelMax;
    }
    public final long getFrameMax()
    {
        return _frameMax;
    }
    public final int getHeartbeat()
    {
        return _heartbeat;
    }

    protected int getBodySize()
    {
        int size = 8;
        return size;
    }

    public void writeMethodPayload(DataOutput buffer) throws IOException
    {
        writeUnsignedShort( buffer, _channelMax );
        writeUnsignedInteger( buffer, _frameMax );
        writeUnsignedShort( buffer, _heartbeat );
    }

    public boolean execute(MethodDispatcher dispatcher, int channelId) throws AMQException
	{
    return ((MethodDispatcher_8_0)dispatcher).dispatchConnectionTune(this, channelId);
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder("[ConnectionTuneBodyImpl: ");
        buf.append( "channelMax=" );
        buf.append(  getChannelMax() );
        buf.append( ", " );
        buf.append( "frameMax=" );
        buf.append(  getFrameMax() );
        buf.append( ", " );
        buf.append( "heartbeat=" );
        buf.append(  getHeartbeat() );
        buf.append("]");
        return buf.toString();
    }

}
