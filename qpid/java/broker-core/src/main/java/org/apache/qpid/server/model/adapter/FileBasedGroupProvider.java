/*
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
package org.apache.qpid.server.model.adapter;

import java.io.File;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.group.FileGroupManager;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.util.MapValueConverter;

@ManagedObject( category = false, type = "GroupFile" )
public class FileBasedGroupProvider
        extends AbstractConfiguredObject<FileBasedGroupProvider> implements GroupProvider<FileBasedGroupProvider>
{
    private static Logger LOGGER = Logger.getLogger(FileBasedGroupProvider.class);

    private GroupManager _groupManager;
    private final Broker<?> _broker;
    private AtomicReference<State> _state;

    @ManagedAttributeField
    private String _path;

    public FileBasedGroupProvider(UUID id,
                                  Broker broker,
                                  Map<String, Object> attributes)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, broker),
              combineIdWithAttributes(id, attributes), broker.getTaskExecutor());


        _broker = broker;

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
    }

    public void validate()
    {
        Collection<GroupProvider<?>> groupProviders = _broker.getGroupProviders();
        for(GroupProvider<?> provider : groupProviders)
        {
            if(provider instanceof FileBasedGroupProvider && provider != this)
            {
                try
                {
                    if(new File(getPath()).getCanonicalPath().equals(new File(((FileBasedGroupProvider)provider).getPath()).getCanonicalPath()))
                    {
                        throw new IllegalConfigurationException("Cannot have two group providers using the same file: " + getPath());
                    }
                }
                catch (IOException e)
                {
                    throw new IllegalArgumentException("Invalid path", e);
                }
            }
        }
    }

    protected void onOpen()
    {
        super.onOpen();
        if(_groupManager == null)
        {
            _groupManager = new FileGroupManager(getPath());
        }
    }

    @Override
    protected void onCreate()
    {
        super.onCreate();
        _groupManager = new FileGroupManager(getPath());
        _groupManager.onCreate();
    }

    @ManagedAttribute( automate = true, mandatory = true)
    public String getPath()
    {
        return _path;
    }

    @Override
    public String setName(String currentName, String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;
    }

    @Override
    public State getState()
    {
        return _state.get();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
            LifetimePolicy desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        return null;
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(getClass());
    }

    @Override
    public Object getAttribute(String name)
    {
        if (DURABLE.equals(name))
        {
            return true;
        }
        else if (LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if (STATE.equals(name))
        {
            return getState();
        }

        return super.getAttribute(name);
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if (childClass == Group.class)
        {
            String groupName = (String) attributes.get(Group.NAME);

            getSecurityManager().authoriseGroupOperation(Operation.CREATE, groupName);
                _groupManager.createGroup(groupName);
            Map<String,Object> attrMap = new HashMap<String, Object>();
            UUID id = UUIDGenerator.generateGroupUUID(getName(),groupName);
            attrMap.put(Group.ID, id);
            attrMap.put(Group.NAME, groupName);
                return (C) new GroupAdapter(attrMap, getTaskExecutor());

        }

        throw new IllegalArgumentException(
                "This group provider does not support creating children of type: "
                        + childClass);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if (clazz == Group.class)
        {
            Set<Principal> groups = _groupManager == null ? Collections.<Principal>emptySet() : _groupManager.getGroupPrincipals();
            Collection<Group> principals = new ArrayList<Group>(groups.size());
            for (Principal group : groups)
            {
                Map<String,Object> attrMap = new HashMap<String, Object>();
                UUID id = UUIDGenerator.generateGroupUUID(getName(),group.getName());
                attrMap.put(Group.ID, id);
                attrMap.put(Group.NAME, group.getName());
                principals.add(new GroupAdapter(attrMap, getTaskExecutor()));
            }
            return (Collection<C>) Collections
                    .unmodifiableCollection(principals);
        }
        else
        {
            return null;
        }
    }

    public GroupManager getGroupManager()
    {
        return _groupManager;
    }

    private SecurityManager getSecurityManager()
    {
        return _broker.getSecurityManager();
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED || state == State.STOPPED)
                    && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    _groupManager.open();
                    return true;
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    if (_broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate group provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Cannot activate group provider in state: " + state);
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                _groupManager.close();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop group provider in state: " + state);
            }
        }
        else if (desiredState == State.DELETED)
        {
            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED || state == State.ERRORED)
                    && _state.compareAndSet(state, State.DELETED))
            {
                _groupManager.close();
                _groupManager.onDelete();
                deleted();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot delete group provider in state: " + state);
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        return false;
    }

    public Set<Principal> getGroupPrincipalsForUser(String username)
    {
        return _groupManager.getGroupPrincipalsForUser(username);
    }

    @Override
    protected void childAdded(ConfiguredObject child)
    {
        // no-op, prevent storing groups in the broker store
    }

    @Override
    protected void childRemoved(ConfiguredObject child)
    {
        // no-op, as per above, groups are not in the store
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of groups provider is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of group provider attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), GroupProvider.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of group provider attributes is denied");
        }
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        throw new UnsupportedOperationException("Changing attributes on group providers is not supported.");
    }


    private class GroupAdapter extends AbstractConfiguredObject<GroupAdapter> implements Group<GroupAdapter>
    {

        public GroupAdapter(Map<String,Object> attributes, TaskExecutor taskExecutor)
        {
            super(attributes, taskExecutor);
        }


        @Override
        public String setName(String currentName, String desiredName)
                throws IllegalStateException, AccessControlException
        {
            throw new IllegalStateException("Names cannot be updated");
        }

        @Override
        public State getState()
        {
            return State.ACTIVE;
        }

        @Override
        public boolean isDurable()
        {
            return true;
        }

        @Override
        public void setDurable(boolean durable) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new IllegalStateException("Durability cannot be updated");
        }

        @Override
        public LifetimePolicy getLifetimePolicy()
        {
            return LifetimePolicy.PERMANENT;
        }

        @Override
        public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
                LifetimePolicy desired) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new IllegalStateException("LifetimePolicy cannot be updated");
        }

        @Override
        public <C extends ConfiguredObject> Collection<C> getChildren(
                Class<C> clazz)
        {
            if (clazz == GroupMember.class)
            {
                Set<Principal> usersInGroup = _groupManager
                        .getUserPrincipalsForGroup(getName());
                Collection<GroupMember> members = new ArrayList<GroupMember>();
                for (Principal principal : usersInGroup)
                {
                    UUID id = UUIDGenerator.generateGroupMemberUUID(FileBasedGroupProvider.this.getName(), getName(), principal.getName());
                    Map<String,Object> attrMap = new HashMap<String, Object>();
                    attrMap.put(GroupMember.ID,id);
                    attrMap.put(GroupMember.NAME, principal.getName());
                    members.add(new GroupMemberAdapter(attrMap, getTaskExecutor()));
                }
                return (Collection<C>) Collections
                        .unmodifiableCollection(members);
            }
            else
            {
                return null;
            }

        }

        @Override
        public <C extends ConfiguredObject> C addChild(Class<C> childClass,
                Map<String, Object> attributes,
                ConfiguredObject... otherParents)
        {
            if (childClass == GroupMember.class)
            {
                String memberName = (String) attributes.get(GroupMember.NAME);

                getSecurityManager().authoriseGroupOperation(Operation.UPDATE, getName());

                _groupManager.addUserToGroup(memberName, getName());
                UUID id = UUIDGenerator.generateGroupMemberUUID(FileBasedGroupProvider.this.getName(), getName(), memberName);
                Map<String,Object> attrMap = new HashMap<String, Object>();
                attrMap.put(GroupMember.ID,id);
                attrMap.put(GroupMember.NAME, memberName);
                return (C) new GroupMemberAdapter(attrMap, getTaskExecutor());

            }

            throw new IllegalArgumentException(
                    "This group provider does not support creating children of type: "
                            + childClass);
        }

        @Override
        public Collection<String> getAttributeNames()
        {
            return getAttributeNames(Group.class);
        }

        @Override
        public Object getAttribute(String name)
        {
            if (ID.equals(name))
            {
                return getId();
            }
            else if (NAME.equals(name))
            {
                return getName();
            }
            return super.getAttribute(name);
        }

        @Override
        protected boolean setState(State currentState, State desiredState)
                throws IllegalStateTransitionException, AccessControlException
        {
            if (desiredState == State.DELETED)
            {
                getSecurityManager().authoriseGroupOperation(Operation.DELETE, getName());
                _groupManager.removeGroup(getName());
                return true;
            }

            return false;
        }

        @Override
        public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
                AccessControlException, IllegalArgumentException
        {
            throw new UnsupportedOperationException("Changing attributes on group is not supported.");
        }

        @Override
        public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
                IllegalArgumentException
        {
            throw new UnsupportedOperationException("Changing attributes on group is not supported.");
        }

        private class GroupMemberAdapter extends AbstractConfiguredObject<GroupMemberAdapter> implements
                GroupMember<GroupMemberAdapter>
        {

            public GroupMemberAdapter(Map<String,Object> attrMap, TaskExecutor taskExecutor)
            {
                super(attrMap, taskExecutor);
            }

            @Override
            public Collection<String> getAttributeNames()
            {
                return getAttributeNames(GroupMember.class);
            }


            @Override
            public String setName(String currentName, String desiredName)
                    throws IllegalStateException, AccessControlException
            {
                return null;
            }

            @Override
            public State getState()
            {
                return null;
            }

            @Override
            public boolean isDurable()
            {
                return false;
            }

            @Override
            public void setDurable(boolean durable)
                    throws IllegalStateException, AccessControlException,
                    IllegalArgumentException
            {
            }

            @Override
            public LifetimePolicy getLifetimePolicy()
            {
                return null;
            }

            @Override
            public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
                    LifetimePolicy desired) throws IllegalStateException,
                    AccessControlException, IllegalArgumentException
            {
                return null;
            }

            @Override
            public <C extends ConfiguredObject> Collection<C> getChildren(
                    Class<C> clazz)
            {
                return null;
            }

            @Override
            protected boolean setState(State currentState, State desiredState)
                    throws IllegalStateTransitionException,
                    AccessControlException
            {
                if (desiredState == State.DELETED)
                {
                    getSecurityManager().authoriseGroupOperation(Operation.UPDATE, GroupAdapter.this.getName());

                    _groupManager.removeUserFromGroup(getName(), GroupAdapter.this.getName());
                    return true;

                }
                return false;
            }

            @Override
            public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
                    AccessControlException, IllegalArgumentException
            {
                throw new UnsupportedOperationException("Changing attributes on group member is not supported.");
            }

            @Override
            public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
                    IllegalArgumentException
            {
                throw new UnsupportedOperationException("Changing attributes on group member is not supported.");
            }
        }
    }


}