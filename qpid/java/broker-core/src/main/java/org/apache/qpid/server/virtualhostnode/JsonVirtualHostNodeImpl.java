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
package org.apache.qpid.server.virtualhostnode;

import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.store.JsonFileConfigStoreFactory;

@ManagedObject(category=false, type="JSON")
public class JsonVirtualHostNodeImpl extends AbstractStandardVirtualHostNode<JsonVirtualHostNodeImpl> implements FileBasedVirtualHostNode<JsonVirtualHostNodeImpl>
{
    @ManagedAttributeField
    private String _storePath;

    @ManagedObjectFactoryConstructor
    public JsonVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> parent)
    {
        super(attributes, parent);
    }

    @Override
    protected DurableConfigurationStoreFactory getDurableConfigurationStoreFactory()
    {
        return new JsonFileConfigStoreFactory();
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", storePath=" + getStorePath() + "]";
    }

}
