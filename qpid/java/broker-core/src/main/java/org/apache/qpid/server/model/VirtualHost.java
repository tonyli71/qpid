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

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.store.MessageStore;

@ManagedObject( managesChildren = true, defaultType = "STANDARD")
public interface VirtualHost<X extends VirtualHost<X, Q, E>, Q extends Queue<?>, E extends Exchange<?> > extends ConfiguredObject<X>
{

    String QUEUE_DEAD_LETTER_QUEUE_ENABLED            = "queue.deadLetterQueueEnabled";

    String HOUSEKEEPING_CHECK_PERIOD            = "housekeepingCheckPeriod";
    String STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = "storeTransactionIdleTimeoutClose";
    String STORE_TRANSACTION_IDLE_TIMEOUT_WARN  = "storeTransactionIdleTimeoutWarn";
    String STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = "storeTransactionOpenTimeoutClose";
    String STORE_TRANSACTION_OPEN_TIMEOUT_WARN  = "storeTransactionOpenTimeoutWarn";
    String SUPPORTED_EXCHANGE_TYPES             = "supportedExchangeTypes";
    String SUPPORTED_QUEUE_TYPES                = "supportedQueueTypes";
    String HOUSE_KEEPING_THREAD_COUNT           = "houseKeepingThreadCount";
    String MESSAGE_STORE_SETTINGS               = "messageStoreSettings";
    String MODEL_VERSION                        = "modelVersion";

    // TODO - this isn't really an attribute
    @ManagedAttribute( derived = true )
    Collection<String> getSupportedExchangeTypes();

    // TODO - this isn't really an attribute
    @ManagedAttribute( derived = true )
    Collection<String> getSupportedQueueTypes();

    @ManagedContextDefault( name = "queue.deadLetterQueueEnabled")
    public static final boolean DEFAULT_DEAD_LETTER_QUEUE_ENABLED = false;

    @ManagedAttribute( automate = true, defaultValue = "${queue.deadLetterQueueEnabled}")
    boolean isQueue_deadLetterQueueEnabled();

    @ManagedContextDefault( name = "virtualhost.housekeepingCheckPeriod")
    public static final long DEFAULT_HOUSEKEEPING_CHECK_PERIOD = 30000l;

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.housekeepingCheckPeriod}")
    long getHousekeepingCheckPeriod();

    @ManagedContextDefault( name = "virtualhost.storeTransactionIdleTimeoutClose")
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = 0l;

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.storeTransactionIdleTimeoutClose}")
    long getStoreTransactionIdleTimeoutClose();

    @ManagedContextDefault( name = "virtualhost.storeTransactionIdleTimeoutWarn")
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_WARN = 0l;

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.storeTransactionIdleTimeoutWarn}")
    long getStoreTransactionIdleTimeoutWarn();

    @ManagedContextDefault( name = "virtualhost.storeTransactionOpenTimeoutClose")
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = 0l;

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.storeTransactionOpenTimeoutClose}")
    long getStoreTransactionOpenTimeoutClose();

    @ManagedContextDefault( name = "virtualhost.storeTransactionOpenTimeoutWarn")
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_WARN = 0l;

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.storeTransactionOpenTimeoutWarn}")
    long getStoreTransactionOpenTimeoutWarn();

    @ManagedContextDefault( name = "virtualhost.housekeepingThreadCount")
    public static final RuntimeDefault<Integer> DEFAULT_HOUSEKEEPING_THREAD_COUNT =
            new RuntimeDefault<Integer>()
            {
                @Override
                public Integer value()
                {
                    return Runtime.getRuntime().availableProcessors();
                }
            };

    @ManagedAttribute( automate = true, defaultValue = "${virtualhost.housekeepingThreadCount}")
    int getHousekeepingThreadCount();

    @ManagedAttribute( automate = true )
    Map<String, Object> getMessageStoreSettings();

    @ManagedAttribute( derived = true )
    String getModelVersion();

    @ManagedStatistic
    long getQueueCount();

    @ManagedStatistic
    long getExchangeCount();

    @ManagedStatistic
    long getConnectionCount();

    @ManagedStatistic
    long getBytesIn();

    @ManagedStatistic
    long getBytesOut();

    @ManagedStatistic
    long getMessagesIn();

    @ManagedStatistic
    long getMessagesOut();

    //children
    Collection<VirtualHostAlias> getAliases();
    Collection<Connection> getConnections();
    Collection<Q> getQueues();
    Collection<E> getExchanges();

    E createExchange(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Q createQueue(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException;

    Collection<String> getExchangeTypeNames();

    public static interface Transaction
    {
        void dequeue(MessageInstance entry);

        void copy(MessageInstance entry, Queue queue);

        void move(MessageInstance entry, Queue queue);

    }

    public static interface TransactionalOperation
    {
        void withinTransaction(Transaction txn);
    }

    void executeTransaction(TransactionalOperation op);

    // TODO - remove this
    TaskExecutor getTaskExecutor();

    E getExchange(UUID id);

    MessageStore getMessageStore();

    String getType();
}
