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
package org.apache.qpid.systest.rest;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Session;
import javax.servlet.http.HttpServletResponse;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.LastValueQueue;
import org.apache.qpid.server.queue.PriorityQueue;
import org.apache.qpid.server.queue.SortedQueue;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;

import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.util.FileUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

public class VirtualHostRestTest extends QpidRestTestCase
{
    private static final String VIRTUALHOST_EXCHANGES_ATTRIBUTE = "exchanges";
    public static final String VIRTUALHOST_QUEUES_ATTRIBUTE = "queues";
    public static final String VIRTUALHOST_CONNECTIONS_ATTRIBUTE = "connections";

    private AMQConnection _connection;

    public void testGet() throws Exception
    {
        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("/rest/virtualhost");
        assertNotNull("Hosts data cannot be null", hosts);
        assertEquals("Unexpected number of hosts", EXPECTED_VIRTUALHOSTS.length, hosts.size());
        for (String hostName : EXPECTED_VIRTUALHOSTS)
        {
            Map<String, Object> host = getRestTestHelper().find("name", hostName, hosts);
            Asserts.assertVirtualHost(hostName, host);
        }
    }

    public void testGetHost() throws Exception
    {
        // create AMQP connection to get connection JSON details
        _connection = (AMQConnection) getConnection();
        Session session = _connection.createSession(true, Session.SESSION_TRANSACTED);
        session.createConsumer(getTestQueue());

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");
        Asserts.assertVirtualHost("test", hostDetails);

        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) hostDetails.get(Asserts.STATISTICS_ATTRIBUTE);

        assertEquals("Unexpected number of exchanges in statistics", EXPECTED_EXCHANGES.length, statistics.get(
                "exchangeCount"));
        assertEquals("Unexpected number of queues in statistics", 1, statistics.get("queueCount"));
        assertEquals("Unexpected number of connections in statistics", 1, statistics.get("connectionCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) hostDetails.get(VIRTUALHOST_EXCHANGES_ATTRIBUTE);
        assertEquals("Unexpected number of exchanges", EXPECTED_EXCHANGES.length, exchanges.size());
        Asserts.assertDurableExchange("amq.fanout", "fanout", getRestTestHelper().find(Exchange.NAME, "amq.fanout", exchanges));
        Asserts.assertDurableExchange("amq.topic", "topic", getRestTestHelper().find(Exchange.NAME, "amq.topic", exchanges));
        Asserts.assertDurableExchange("amq.direct", "direct", getRestTestHelper().find(Exchange.NAME, "amq.direct", exchanges));
        Asserts.assertDurableExchange("amq.match", "headers", getRestTestHelper().find(Exchange.NAME, "amq.match", exchanges));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VIRTUALHOST_QUEUES_ATTRIBUTE);
        assertEquals("Unexpected number of queues", 1, queues.size());
        Map<String, Object> queue = getRestTestHelper().find(Queue.NAME,  getTestQueueName(), queues);
        Asserts.assertQueue(getTestQueueName(), "standard", queue);
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, queue.get(Queue.DURABLE));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> connections = (List<Map<String, Object>>) hostDetails
                .get(VIRTUALHOST_CONNECTIONS_ATTRIBUTE);
        assertEquals("Unexpected number of connections", 1, connections.size());
        Asserts.assertConnection(connections.get(0), _connection);
    }

    public void testPutCreateVirtualHostUsingStoreType() throws Exception
    {
        String hostName = getTestName();
        String storeType = getTestProfileVirtualHostNodeType();
        String storeLocation = createHost(hostName, storeType, null);
        try
        {
            // make sure that the host is saved in the broker store
            restartBroker();
            Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/" + hostName);
            Asserts.assertVirtualHost(hostName, hostDetails);

            assertNewVirtualHost(hostDetails);
        }
        finally
        {
            if (storeLocation != null)
            {
                FileUtils.delete(new File(storeLocation), true);
            }
        }
    }

    public void testDeleteHost() throws Exception
    {
        String hostToDelete = TEST3_VIRTUALHOST;
        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/virtualhost/" + hostToDelete + "/" + hostToDelete, "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());

        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("/rest/virtualhost/" + hostToDelete);
        assertEquals("Host should be deleted", 0, hosts.size());
    }

    public void testDeleteDefaultHostFails() throws Exception
    {
        String hostToDelete = TEST1_VIRTUALHOST;
        int response = getRestTestHelper().submitRequest("/rest/virtualhost/" + hostToDelete, "DELETE", null);
        assertEquals("Unexpected response code", 409, response);

        restartBroker();

        List<Map<String, Object>> hosts = getRestTestHelper().getJsonAsList("/rest/virtualhost/" + hostToDelete);
        assertEquals("Host should be deleted", 1, hosts.size());
    }

    public void testUpdateActiveHostFails() throws Exception
    {
        String hostToUpdate = TEST3_VIRTUALHOST;
        String restHostUrl = "/rest/virtualhost/" + hostToUpdate + "/" + hostToUpdate;
        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList(restHostUrl);
        Asserts.assertVirtualHost(hostToUpdate, hostDetails);

        Map<String, Object> newAttributes = new HashMap<String, Object>();
        newAttributes.put(VirtualHost.NAME, hostToUpdate);
        newAttributes.put("fakeAttribute", "value");

        int response = getRestTestHelper().submitRequest(restHostUrl, "PUT", newAttributes);
        assertEquals("Unexpected response code", 409, response);

        restartBroker();

        Map<String, Object> rereadHostDetails = getRestTestHelper().getJsonAsSingletonList(restHostUrl);
        Asserts.assertVirtualHost(hostToUpdate, rereadHostDetails);
        assertFalse(rereadHostDetails.containsKey("fakeAttribute"));
    }

    public void testPutCreateQueue() throws Exception
    {
        String queueName = getTestQueueName();

        createQueue(queueName + "-standard", "standard", null);

        Map<String, Object> sortedQueueAttributes = new HashMap<String, Object>();
        sortedQueueAttributes.put(SortedQueue.SORT_KEY, "sortme");
        createQueue(queueName + "-sorted", "sorted", sortedQueueAttributes);

        Map<String, Object> priorityQueueAttributes = new HashMap<String, Object>();
        priorityQueueAttributes.put(PriorityQueue.PRIORITIES, 10);
        createQueue(queueName + "-priority", "priority", priorityQueueAttributes);

        Map<String, Object> lvqQueueAttributes = new HashMap<String, Object>();
        lvqQueueAttributes.put(LastValueQueue.LVQ_KEY, "LVQ");
        createQueue(queueName + "-lvq", "lvq", lvqQueueAttributes);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> standardQueue = getRestTestHelper().find(Queue.NAME, queueName + "-standard" , queues);
        Map<String, Object> sortedQueue = getRestTestHelper().find(Queue.NAME, queueName + "-sorted" , queues);
        Map<String, Object> priorityQueue = getRestTestHelper().find(Queue.NAME, queueName + "-priority" , queues);
        Map<String, Object> lvqQueue = getRestTestHelper().find(Queue.NAME, queueName + "-lvq" , queues);

        Asserts.assertQueue(queueName + "-standard", "standard", standardQueue);
        Asserts.assertQueue(queueName + "-sorted", "sorted", sortedQueue);
        Asserts.assertQueue(queueName + "-priority", "priority", priorityQueue);
        Asserts.assertQueue(queueName + "-lvq", "lvq", lvqQueue);

        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, standardQueue.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, sortedQueue.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, priorityQueue.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, lvqQueue.get(Queue.DURABLE));

        assertEquals("Unexpected sorted key attribute", "sortme", sortedQueue.get(SortedQueue.SORT_KEY));
        assertEquals("Unexpected lvq key attribute", "LVQ", lvqQueue.get(LastValueQueue.LVQ_KEY));
        assertEquals("Unexpected priorities key attribute", 10, priorityQueue.get(PriorityQueue.PRIORITIES));
    }

    public void testPutCreateExchange() throws Exception
    {
        String exchangeName = getTestName();

        createExchange(exchangeName + "-direct", "direct");
        createExchange(exchangeName + "-topic", "topic");
        createExchange(exchangeName + "-headers", "headers");
        createExchange(exchangeName + "-fanout", "fanout");

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_EXCHANGES_ATTRIBUTE);
        Map<String, Object> directExchange = getRestTestHelper().find(Queue.NAME, exchangeName + "-direct" , exchanges);
        Map<String, Object> topicExchange = getRestTestHelper().find(Queue.NAME, exchangeName + "-topic" , exchanges);
        Map<String, Object> headersExchange = getRestTestHelper().find(Queue.NAME, exchangeName + "-headers" , exchanges);
        Map<String, Object> fanoutExchange = getRestTestHelper().find(Queue.NAME, exchangeName + "-fanout" , exchanges);

        Asserts.assertDurableExchange(exchangeName + "-direct", "direct", directExchange);
        Asserts.assertDurableExchange(exchangeName + "-topic", "topic", topicExchange);
        Asserts.assertDurableExchange(exchangeName + "-headers", "headers", headersExchange);
        Asserts.assertDurableExchange(exchangeName + "-fanout", "fanout", fanoutExchange);

        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, directExchange.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, topicExchange.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, headersExchange.get(Queue.DURABLE));
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, fanoutExchange.get(Queue.DURABLE));

    }

    public void testPutCreateLVQWithoutKey() throws Exception
    {
        String queueName = getTestQueueName()+ "-lvq";
        createQueue(queueName, "lvq", null);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> lvqQueue = getRestTestHelper().find(Queue.NAME, queueName  , queues);

        Asserts.assertQueue(queueName , "lvq", lvqQueue);
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, lvqQueue.get(Queue.DURABLE));
        assertEquals("Unexpected lvq key attribute", LastValueQueue.DEFAULT_LVQ_KEY, lvqQueue.get(LastValueQueue.LVQ_KEY));
    }

    public void testPutCreateSortedQueueWithoutKey() throws Exception
    {
        String queueName = getTestQueueName() + "-sorted";
        int responseCode = tryCreateQueue(queueName, "sorted", null);
        assertEquals("Unexpected response code", HttpServletResponse.SC_CONFLICT, responseCode);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> testQueue = getRestTestHelper().find(Queue.NAME, queueName  , queues);

        assertNull("Sorted queue without a key was created ", testQueue);
    }

    public void testPutCreatePriorityQueueWithoutKey() throws Exception
    {
        String queueName = getTestQueueName()+ "-priority";
        createQueue(queueName, "priority", null);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> priorityQueue = getRestTestHelper().find(Queue.NAME, queueName  , queues);

        Asserts.assertQueue(queueName , "priority", priorityQueue);
        assertEquals("Unexpected value of queue attribute " + Queue.DURABLE, Boolean.TRUE, priorityQueue.get(Queue.DURABLE));
        assertEquals("Unexpected number of priorities", 10, priorityQueue.get(PriorityQueue.PRIORITIES));
    }

    public void testPutCreateStandardQueueWithoutType() throws Exception
    {
        String queueName = getTestQueueName();
        createQueue(queueName, null, null);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> queue = getRestTestHelper().find(Queue.NAME, queueName  , queues);

        Asserts.assertQueue(queueName , "standard", queue);
    }

    public void testPutCreateQueueOfUnsupportedType() throws Exception
    {
        String queueName = getTestQueueName();
        int responseCode = tryCreateQueue(queueName, "unsupported", null);
        assertEquals("Unexpected response code", HttpServletResponse.SC_CONFLICT, responseCode);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> queue = getRestTestHelper().find(Queue.NAME, queueName  , queues);

        assertNull("Queue of unsupported type was created", queue);
    }

    public void testDeleteQueue() throws Exception
    {
        String queueName = getTestQueueName();
        createQueue(queueName, null, null);

        String queueUrl = "/rest/queue/test/test/" + queueName;
        List<Map<String, Object>> queues = getRestTestHelper().getJsonAsList(queueUrl);
        assertEquals("Queue should exist", 1, queues.size());

        HttpURLConnection connection = getRestTestHelper().openManagementConnection(queueUrl, "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        queues = getRestTestHelper().getJsonAsList(queueUrl);
        assertEquals("Queue should be deleted", 0, queues.size());
    }

    public void testDeleteQueueById() throws Exception
    {
        String queueName = getTestQueueName();
        createQueue(queueName, null, null);
        Map<String, Object> queueDetails = getRestTestHelper().getJsonAsSingletonList("/rest/queue/test/test/" + queueName);

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/queue/test/test?id=" + queueDetails.get(Queue.ID), "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> queues = getRestTestHelper().getJsonAsList("/rest/queue/test/test/" + queueName);
        assertEquals("Queue should be deleted", 0, queues.size());
    }

    public void testDeleteExchange() throws Exception
    {
        String exchangeName = getTestName();
        createExchange(exchangeName, "direct");

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/exchange/test/test/" + exchangeName, "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> queues = getRestTestHelper().getJsonAsList("/rest/exchange/test/test/" + exchangeName);
        assertEquals("Exchange should be deleted", 0, queues.size());
    }

    public void testDeleteExchangeById() throws Exception
    {
        String exchangeName = getTestName();
        createExchange(exchangeName, "direct");
        Map<String, Object> echangeDetails = getRestTestHelper().getJsonAsSingletonList("/rest/exchange/test/test/" + exchangeName);

        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/exchange/test/test?id=" + echangeDetails.get(Exchange.ID), "DELETE");
        connection.connect();
        assertEquals("Unexpected response code", 200, connection.getResponseCode());
        List<Map<String, Object>> queues = getRestTestHelper().getJsonAsList("/rest/exchange/test/test/" + exchangeName);
        assertEquals("Exchange should be deleted", 0, queues.size());
    }

    public void testPutCreateQueueWithAttributes() throws Exception
    {
        String queueName = getTestQueueName();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ALERT_REPEAT_GAP, 1000);
        attributes.put(Queue.ALERT_THRESHOLD_MESSAGE_AGE, 3600000);
        attributes.put(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, 1000000000);
        attributes.put(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, 800);
        attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 15);
        attributes.put(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 2000000000);
        attributes.put(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 1500000000);

        createQueue(queueName + "-standard", "standard", attributes);

        Map<String, Object> sortedQueueAttributes = new HashMap<String, Object>();
        sortedQueueAttributes.putAll(attributes);
        sortedQueueAttributes.put(SortedQueue.SORT_KEY, "sortme");
        createQueue(queueName + "-sorted", "sorted", sortedQueueAttributes);

        Map<String, Object> priorityQueueAttributes = new HashMap<String, Object>();
        priorityQueueAttributes.putAll(attributes);
        priorityQueueAttributes.put(PriorityQueue.PRIORITIES, 10);
        createQueue(queueName + "-priority", "priority", priorityQueueAttributes);

        Map<String, Object> lvqQueueAttributes = new HashMap<String, Object>();
        lvqQueueAttributes.putAll(attributes);
        lvqQueueAttributes.put(LastValueQueue.LVQ_KEY, "LVQ");
        createQueue(queueName + "-lvq", "lvq", lvqQueueAttributes);

        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        Map<String, Object> standardQueue = getRestTestHelper().find(Queue.NAME, queueName + "-standard" , queues);
        Map<String, Object> sortedQueue = getRestTestHelper().find(Queue.NAME, queueName + "-sorted" , queues);
        Map<String, Object> priorityQueue = getRestTestHelper().find(Queue.NAME, queueName + "-priority" , queues);
        Map<String, Object> lvqQueue = getRestTestHelper().find(Queue.NAME, queueName + "-lvq" , queues);

        attributes.put(Queue.DURABLE, Boolean.TRUE);
        Asserts.assertQueue(queueName + "-standard", "standard", standardQueue, attributes);
        Asserts.assertQueue(queueName + "-sorted", "sorted", sortedQueue, attributes);
        Asserts.assertQueue(queueName + "-priority", "priority", priorityQueue, attributes);
        Asserts.assertQueue(queueName + "-lvq", "lvq", lvqQueue, attributes);

        assertEquals("Unexpected sorted key attribute", "sortme", sortedQueue.get(SortedQueue.SORT_KEY));
        assertEquals("Unexpected lvq key attribute", "LVQ", lvqQueue.get(LastValueQueue.LVQ_KEY));
        assertEquals("Unexpected priorities key attribute", 10, priorityQueue.get(PriorityQueue.PRIORITIES));
    }

    @SuppressWarnings("unchecked")
    public void testCreateQueueWithDLQEnabled() throws Exception
    {
        String queueName = getTestQueueName();

        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AbstractVirtualHost.CREATE_DLQ_ON_CREATION, true);

        //verify the starting state
        Map<String, Object> hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");
        List<Map<String, Object>> queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_EXCHANGES_ATTRIBUTE);

        assertNull("queue "+ queueName + " should not have already been present", getRestTestHelper().find(Queue.NAME, queueName , queues));
        assertNull("queue "+ queueName + "_DLQ should not have already been present", getRestTestHelper().find(Queue.NAME, queueName + "_DLQ" , queues));
        assertNull("exchange should not have already been present", getRestTestHelper().find(Exchange.NAME, queueName + "_DLE" , exchanges));

        //create the queue
        createQueue(queueName, "standard", attributes);

        //verify the new queue, as well as the DLQueue and DLExchange have been created
        hostDetails = getRestTestHelper().getJsonAsSingletonList("/rest/virtualhost/test");
        queues = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_QUEUES_ATTRIBUTE);
        exchanges = (List<Map<String, Object>>) hostDetails.get(VirtualHostRestTest.VIRTUALHOST_EXCHANGES_ATTRIBUTE);

        Map<String, Object> queue = getRestTestHelper().find(Queue.NAME, queueName , queues);
        Map<String, Object> dlqQueue = getRestTestHelper().find(Queue.NAME, queueName + "_DLQ" , queues);
        Map<String, Object> dlExchange = getRestTestHelper().find(Exchange.NAME, queueName + "_DLE" , exchanges);
        assertNotNull("queue should have been present", queue);
        assertNotNull("queue should have been present", dlqQueue);
        assertNotNull("exchange should have been present", dlExchange);

        //verify that the alternate exchange is set as expected on the new queue
        Map<String, Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ALTERNATE_EXCHANGE, queueName + "_DLE");

        Asserts.assertQueue(queueName, "standard", queue, queueAttributes);
        Asserts.assertQueue(queueName, "standard", queue, null);
    }

    private void createExchange(String exchangeName, String exchangeType) throws IOException
    {
        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/exchange/test/test/" + exchangeName, "PUT");

        Map<String, Object> queueData = new HashMap<String, Object>();
        queueData.put(Exchange.NAME, exchangeName);
        queueData.put(Exchange.DURABLE, Boolean.TRUE);
        queueData.put(Exchange.TYPE, exchangeType);

        getRestTestHelper().writeJsonRequest(connection, queueData);
        assertEquals("Unexpected response code", 201, connection.getResponseCode());

        connection.disconnect();
    }

    private void createQueue(String queueName, String queueType, Map<String, Object> attributes) throws IOException,
            JsonGenerationException, JsonMappingException
    {
        int responseCode = tryCreateQueue(queueName, queueType, attributes);
        assertEquals("Unexpected response code", 201, responseCode);
    }

    private int tryCreateQueue(String queueName, String queueType, Map<String, Object> attributes) throws IOException,
            JsonGenerationException, JsonMappingException
    {
        HttpURLConnection connection = getRestTestHelper().openManagementConnection("/rest/queue/test/test/" + queueName, "PUT");

        Map<String, Object> queueData = new HashMap<String, Object>();
        queueData.put(Queue.NAME, queueName);
        queueData.put(Queue.DURABLE, Boolean.TRUE);
        if (queueType != null)
        {
            queueData.put(Queue.TYPE, queueType);
        }
        if (attributes != null)
        {
            queueData.putAll(attributes);
        }

        getRestTestHelper().writeJsonRequest(connection, queueData);
        int responseCode = connection.getResponseCode();
        connection.disconnect();
        return responseCode;
    }

    private String createHost(String hostName, String storeType, String configPath) throws IOException, JsonGenerationException,
            JsonMappingException
    {
        String storePath = getStoreLocation(hostName);
        int responseCode = tryCreateVirtualHost(hostName, storeType, storePath, configPath);
        assertEquals("Unexpected response code", 201, responseCode);
        return storePath;
    }

    private String getStoreLocation(String hostName)
    {
        return new File(TMP_FOLDER, "store-" + hostName + "-" + System.currentTimeMillis()).getAbsolutePath();
    }

    private int tryCreateVirtualHost(String hostName, String virtualHostNodeType, String storePath, String configPath) throws IOException,
            JsonGenerationException, JsonMappingException
    {

        Map<String, Object> hostData = new HashMap<String, Object>();
        hostData.put(VirtualHostNode.NAME, hostName);
        hostData.put(VirtualHostNode.TYPE, virtualHostNodeType);
        hostData.put(VirtualHostNode.IS_MESSAGE_STORE_PROVIDER, true);
        hostData.put(DurableConfigurationStore.STORE_PATH, storePath);

        return getRestTestHelper().submitRequest("/rest/virtualhostnode/" + hostName, "PUT", hostData);
    }

    private void assertNewVirtualHost(Map<String, Object> hostDetails)
    {
        @SuppressWarnings("unchecked")
        Map<String, Object> statistics = (Map<String, Object>) hostDetails.get(Asserts.STATISTICS_ATTRIBUTE);
        assertEquals("Unexpected number of exchanges in statistics", EXPECTED_EXCHANGES.length,
                statistics.get("exchangeCount"));
        assertEquals("Unexpected number of queues in statistics", 0, statistics.get("queueCount"));
        assertEquals("Unexpected number of connections in statistics", 0, statistics.get("connectionCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) hostDetails.get(VIRTUALHOST_EXCHANGES_ATTRIBUTE);
        assertEquals("Unexpected number of exchanges", EXPECTED_EXCHANGES.length, exchanges.size());
        RestTestHelper restTestHelper = getRestTestHelper();
        Asserts.assertDurableExchange("amq.fanout", "fanout", restTestHelper.find(Exchange.NAME, "amq.fanout", exchanges));
        Asserts.assertDurableExchange("amq.topic", "topic", restTestHelper.find(Exchange.NAME, "amq.topic", exchanges));
        Asserts.assertDurableExchange("amq.direct", "direct", restTestHelper.find(Exchange.NAME, "amq.direct", exchanges));
        Asserts.assertDurableExchange("amq.match", "headers", restTestHelper.find(Exchange.NAME, "amq.match", exchanges));

        assertNull("Unexpected queues", hostDetails.get(VIRTUALHOST_QUEUES_ATTRIBUTE));
        assertNull("Unexpected connections", hostDetails.get(VIRTUALHOST_CONNECTIONS_ATTRIBUTE));
    }

}
