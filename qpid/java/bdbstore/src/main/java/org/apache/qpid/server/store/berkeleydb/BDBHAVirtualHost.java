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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Map;

import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

@ManagedObject( category = false, type = "BDB_HA" )
public class BDBHAVirtualHost extends AbstractVirtualHost<BDBHAVirtualHost>
{
    public static final String TYPE = "BDB_HA";

    private final BDBMessageStore _messageStore;
    private MessageStoreLogSubject _messageStoreLogSubject;

    @ManagedObjectFactoryConstructor
    protected BDBHAVirtualHost(final Map<String, Object> attributes, VirtualHostNode<?> virtualHostNode)
    {
        super(attributes, virtualHostNode);

        _messageStore = (BDBMessageStore) virtualHostNode.getConfigurationStore();
        _messageStoreLogSubject = new MessageStoreLogSubject(getName(), _messageStore.getClass().getSimpleName());
    }

    @Override
    protected void initialiseStorage()
    {
    }

    @Override
    public DurableConfigurationStore getDurableConfigurationStore()
    {
        return _messageStore;
    }

    @Override
    public MessageStore getMessageStore()
    {
        return _messageStore;
    }

    @Override
    protected MessageStoreLogSubject getMessageStoreLogSubject()
    {
        return _messageStoreLogSubject;
    }

}
