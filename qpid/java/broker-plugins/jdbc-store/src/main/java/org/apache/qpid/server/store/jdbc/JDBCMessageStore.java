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
package org.apache.qpid.server.store.jdbc;


import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.apache.qpid.server.plugin.JDBCConnectionProviderFactory;
import org.apache.qpid.server.store.AbstractJDBCMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.Transaction;
import org.apache.qpid.server.util.MapValueConverter;

/**
 * An implementation of a {@link org.apache.qpid.server.store.MessageStore} that uses a JDBC database as the persistence
 * mechanism.
 *
 */
public class JDBCMessageStore extends AbstractJDBCMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(JDBCMessageStore.class);

    public static final String TYPE = "JDBC";
    public static final String CONNECTION_URL = "connectionURL";
    public static final String CONNECTION_POOL_TYPE = "connectionPoolType";
    public static final String JDBC_BIG_INT_TYPE = "bigIntType";
    public static final String JDBC_BYTES_FOR_BLOB = "bytesForBlob";
    public static final String JDBC_VARBINARY_TYPE = "varbinaryType";
    public static final String JDBC_BLOB_TYPE = "blobType";

    protected String _connectionURL;
    private ConnectionProvider _connectionProvider;


    private static class JDBCDetails
    {
        private final String _vendor;
        private String _blobType;
        private String _varBinaryType;
        private String _bigintType;
        private boolean _useBytesMethodsForBlob;

        private JDBCDetails(String vendor,
                            String blobType,
                            String varBinaryType,
                            String bigIntType,
                            boolean useBytesMethodsForBlob)
        {
            _vendor = vendor;
            setBlobType(blobType);
            setVarBinaryType(varBinaryType);
            setBigintType(bigIntType);
            setUseBytesMethodsForBlob(useBytesMethodsForBlob);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            JDBCDetails that = (JDBCDetails) o;

            if (!getVendor().equals(that.getVendor()))
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return getVendor().hashCode();
        }

        @Override
        public String toString()
        {
            return "JDBCDetails{" +
                   "vendor='" + getVendor() + '\'' +
                   ", blobType='" + getBlobType() + '\'' +
                   ", varBinaryType='" + getVarBinaryType() + '\'' +
                   ", bigIntType='" + getBigintType() + '\'' +
                   ", useBytesMethodsForBlob=" + isUseBytesMethodsForBlob() +
                   '}';
        }

        public String getVendor()
        {
            return _vendor;
        }

        public String getBlobType()
        {
            return _blobType;
        }

        public void setBlobType(String blobType)
        {
            _blobType = blobType;
        }

        public String getVarBinaryType()
        {
            return _varBinaryType;
        }

        public void setVarBinaryType(String varBinaryType)
        {
            _varBinaryType = varBinaryType;
        }

        public boolean isUseBytesMethodsForBlob()
        {
            return _useBytesMethodsForBlob;
        }

        public void setUseBytesMethodsForBlob(boolean useBytesMethodsForBlob)
        {
            _useBytesMethodsForBlob = useBytesMethodsForBlob;
        }

        public String getBigintType()
        {
            return _bigintType;
        }

        public void setBigintType(String bigintType)
        {
            _bigintType = bigintType;
        }
    }

    private static JDBCDetails DERBY_DETAILS =
            new JDBCDetails("derby",
                            "blob",
                            "varchar(%d) for bit data",
                            "bigint",
                            false);

    private static JDBCDetails POSTGRESQL_DETAILS =
            new JDBCDetails("postgresql",
                            "bytea",
                            "bytea",
                            "bigint",
                            true);

    private static JDBCDetails MYSQL_DETAILS =
            new JDBCDetails("mysql",
                            "blob",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails SYBASE_DETAILS =
            new JDBCDetails("sybase",
                            "image",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails ORACLE_DETAILS =
            new JDBCDetails("oracle",
                            "blob",
                            "raw(%d)",
                            "number",
                            false);


    private static Map<String, JDBCDetails> VENDOR_DETAILS = new HashMap<String,JDBCDetails>();

    static
    {

        addDetails(DERBY_DETAILS);
        addDetails(POSTGRESQL_DETAILS);
        addDetails(MYSQL_DETAILS);
        addDetails(SYBASE_DETAILS);
        addDetails(ORACLE_DETAILS);
    }

    private static void addDetails(JDBCDetails details)
    {
        VENDOR_DETAILS.put(details.getVendor(), details);
    }

    private String _blobType;
    private String _varBinaryType;
    private String _bigIntType;
    private boolean _useBytesMethodsForBlob;

    private List<RecordedJDBCTransaction> _transactions = new CopyOnWriteArrayList<RecordedJDBCTransaction>();


    public JDBCMessageStore()
    {
    }

    protected Logger getLogger()
    {
        return _logger;
    }

    protected String getSqlBlobType()
    {
        return _blobType;
    }

    protected String getSqlVarBinaryType(int size)
    {
        return String.format(_varBinaryType, size);
    }

    public String getSqlBigIntType()
    {
        return _bigIntType;
    }

    @Override
    protected void doClose()
    {
        try
        {
            while(!_transactions.isEmpty())
            {
                RecordedJDBCTransaction txn = _transactions.get(0);
                txn.abortTran();
            }
        }
        finally
        {
            try
            {
                _connectionProvider.close();
            }
            catch (SQLException e)
            {
                throw new StoreException("Unable to close connection provider ", e);
            }
        }
    }


    protected Connection getConnection() throws SQLException
    {
        return _connectionProvider.getConnection();
    }


    protected void implementationSpecificConfiguration(String name, Map<String, Object> storeSettings)
        throws ClassNotFoundException, SQLException
    {
        _connectionURL = String.valueOf(storeSettings.get(CONNECTION_URL));
        Object poolAttribute = storeSettings.get(CONNECTION_POOL_TYPE);

        JDBCDetails details = null;

        String[] components = _connectionURL.split(":",3);
        if(components.length >= 2)
        {
            String vendor = components[1];
            details = VENDOR_DETAILS.get(vendor);
        }

        if(details == null)
        {
            getLogger().info("Do not recognize vendor from connection URL: " + _connectionURL);

            // TODO - is there a better default than derby
            details = DERBY_DETAILS;
        }

        String connectionPoolType = poolAttribute == null ? DefaultConnectionProviderFactory.TYPE : String.valueOf(poolAttribute);

        JDBCConnectionProviderFactory connectionProviderFactory =
                JDBCConnectionProviderFactory.FACTORIES.get(connectionPoolType);
        if(connectionProviderFactory == null)
        {
            _logger.warn("Unknown connection pool type: " + connectionPoolType + ".  no connection pooling will be used");
            connectionProviderFactory = new DefaultConnectionProviderFactory();
        }

        _connectionProvider = connectionProviderFactory.getConnectionProvider(_connectionURL, storeSettings);
        _blobType = MapValueConverter.getStringAttribute(JDBC_BLOB_TYPE, storeSettings, details.getBlobType());
        _varBinaryType = MapValueConverter.getStringAttribute(JDBC_VARBINARY_TYPE, storeSettings, details.getVarBinaryType());
        _useBytesMethodsForBlob = MapValueConverter.getBooleanAttribute(JDBC_BYTES_FOR_BLOB, storeSettings, details.isUseBytesMethodsForBlob());
        _bigIntType = MapValueConverter.getStringAttribute(JDBC_BIG_INT_TYPE, storeSettings, details.getBigintType());
    }

    @Override
    protected void storedSizeChange(int contentSize)
    {
    }

    @Override
    public String getStoreLocation()
    {
        return _connectionURL;
    }

    @Override
    protected byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        if(_useBytesMethodsForBlob)
        {
            return rs.getBytes(col);
        }
        else
        {
            Blob dataAsBlob = rs.getBlob(col);
            return dataAsBlob.getBytes(1,(int) dataAsBlob.length());

        }
    }

    @Override
    protected String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        byte[] bytes;
        if(_useBytesMethodsForBlob)
        {
            bytes = rs.getBytes(col);
            return new String(bytes,UTF8_CHARSET);
        }
        else
        {
            Blob blob = rs.getBlob(col);
            if(blob == null)
            {
                return null;
            }
            bytes = blob.getBytes(1, (int)blob.length());
        }
        return new String(bytes, UTF8_CHARSET);

    }

    @Override
    public Transaction newTransaction()
    {
        return new RecordedJDBCTransaction();
    }

    private class RecordedJDBCTransaction extends JDBCTransaction
    {
        private RecordedJDBCTransaction()
        {
            super();
            JDBCMessageStore.this._transactions.add(this);
        }

        @Override
        public void commitTran()
        {
            try
            {
                super.commitTran();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public StoreFuture commitTranAsync()
        {
            try
            {
                return super.commitTranAsync();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public void abortTran()
        {
            try
            {
                super.abortTran();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }
    }

}
