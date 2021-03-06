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

import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.logging.subjects.MessageStoreLogSubject;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;

public abstract class AbstractVirtualHostNode<X extends AbstractVirtualHostNode<X>> extends AbstractConfiguredObject<X> implements VirtualHostNode<X>
{

    private static final Logger LOGGER = Logger.getLogger(AbstractVirtualHostNode.class);

    private final Broker<?> _broker;
    private final AtomicReference<State> _state = new AtomicReference<State>(State.INITIALISING);
    private final EventLogger _eventLogger;

    private DurableConfigurationStore _durableConfigurationStore;

    private MessageStoreLogSubject _configurationStoreLogSubject;

    @ManagedAttributeField
    private boolean _messageStoreProvider;

    public AbstractVirtualHostNode(Broker<?> parent, Map<String, Object> attributes)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, parent),
              attributes);
        _broker = parent;
        SystemContext<?> systemContext = _broker.getParent(SystemContext.class);
        _eventLogger = systemContext.getEventLogger();
    }


    @Override
    public void onOpen()
    {
        super.onOpen();
        _durableConfigurationStore = createConfigurationStore();
        _configurationStoreLogSubject = new MessageStoreLogSubject(getName(), _durableConfigurationStore.getClass().getSimpleName());

    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if (state == State.ACTIVE || state == State.INITIALISING)
            {
                state = setDesiredState(currentState, State.STOPPED);
            }

            if (state == State.STOPPED || state == State.ERRORED)
            {
                if( _state.compareAndSet(state, State.DELETED))
                {
                    delete();
                    return true;
                }
            }
            else
            {
                throw new IllegalStateException("Cannot delete virtual host node in " + state + " state");
            }
        }
        else if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.STOPPED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    activate();
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to make " + this + " active.", e);
                    }
                    else
                    {
                        throw e;
                    }
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot activate virtual host node in " + state + " state");
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                stop();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop virtual host node in " + state + " state");
            }
        }
        return false;
    }

    @Override
    public boolean isMessageStoreProvider()
    {
        return _messageStoreProvider;
    }

    @Override
    public VirtualHost<?,?,?> getVirtualHost()
    {
        Collection<VirtualHost> children = getChildren(VirtualHost.class);
        if (children.size() == 0)
        {
            return null;
        }
        else if (children.size() == 1)
        {
            return children.iterator().next();
        }
        else
        {
            throw new IllegalStateException(this + " has an unexpected number of virtualhost children, size " + children.size());
        }
    }

    @Override
    public DurableConfigurationStore getConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    protected Broker<?> getBroker()
    {
        return _broker;
    }

    protected EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    protected DurableConfigurationStore getDurableConfigurationStore()
    {
        return _durableConfigurationStore;
    }

    protected MessageStoreLogSubject getConfigurationStoreLogSubject()
    {
        return _configurationStoreLogSubject;
    }

    protected Map<String, Object> buildAttributesForStore()
    {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
        {
            @Override
            public Object run()
            {
                for (String attributeName : getAttributeNames())
                {
                    Object value = getAttribute(attributeName);
                    attributes.put(attributeName, value);
                }
                return null;
            }
        });

        return attributes;
    }

    protected void delete()
    {
        VirtualHost<?, ?, ?> virtualHost = getVirtualHost();
        if (virtualHost != null)
        {
            virtualHost.setDesiredState(virtualHost.getState(), State.DELETED);
        }

        deleted();

        if (getConfigurationStore() instanceof MessageStore)
        {
            ((MessageStore)getConfigurationStore()).onDelete();
        }
    }

    protected void stop()
    {
        VirtualHost<?, ?, ?> virtualHost = getVirtualHost();
        if (virtualHost != null)
        {
            virtualHost.setDesiredState(virtualHost.getState(), State.STOPPED);
        }
        getConfigurationStore().closeConfigurationStore();

        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.CLOSE());
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), VirtualHostNode.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of virtual host node is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), VirtualHostNode.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of virtual host node attributes is denied");
        }
    }

    protected abstract DurableConfigurationStore createConfigurationStore();

    protected abstract void activate();

}
