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

#ifndef QPID_LEGACYSTORE_MESSAGESTOREIMPL_H
#define QPID_LEGACYSTORE_MESSAGESTOREIMPL_H

#include <string>

#include "db-inc.h"
#include "qpid/linearstore/Cursor.h"
#include "qpid/linearstore/IdDbt.h"
#include "qpid/linearstore/IdSequence.h"
#include "qpid/linearstore/JournalImpl.h"
#include "qpid/linearstore/jrnl/jcfg.h"
#include "qpid/linearstore/PreparedTransaction.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/linearstore/Store.h"
#include "qpid/linearstore/TxnCtxt.h"

// Assume DB_VERSION_MAJOR == 4
#if (DB_VERSION_MINOR == 2)
#include <errno.h>
#define DB_BUFFER_SMALL ENOMEM
#endif

namespace qpid { namespace sys {
class Timer;
}}

namespace qpid{
namespace linearstore{

/**
 * An implementation of the MessageStore interface based on Berkeley DB
 */
class MessageStoreImpl : public qpid::broker::MessageStore, public qpid::management::Manageable
{
  public:
    typedef boost::shared_ptr<Db> db_ptr;
    typedef boost::shared_ptr<DbEnv> dbEnv_ptr;

    struct StoreOptions : public qpid::Options {
        StoreOptions(const std::string& name="Linear Store Options");
        std::string clusterName;
        std::string storeDir;
        bool truncateFlag;
        uint32_t wCachePageSizeKib;
        uint32_t tplWCachePageSizeKib;
        uint16_t efpPartition;
        uint64_t efpFileSize;
    };

  protected:
    typedef std::map<uint64_t, qpid::broker::RecoverableQueue::shared_ptr> queue_index;
    typedef std::map<uint64_t, qpid::broker::RecoverableExchange::shared_ptr> exchange_index;
    typedef std::map<uint64_t, qpid::broker::RecoverableMessage::shared_ptr> message_index;

    typedef LockedMappings::map txn_lock_map;
    typedef boost::ptr_list<PreparedTransaction> txn_list;

    // Structs for Transaction Recover List (TPL) recover state
    struct TplRecoverStruct {
        uint64_t rid; // rid of TPL record
        bool deq_flag;
        bool commit_flag;
        bool tpc_flag;
        TplRecoverStruct(const uint64_t _rid, const bool _deq_flag, const bool _commit_flag, const bool _tpc_flag);
    };
    typedef TplRecoverStruct TplRecover;
    typedef std::pair<std::string, TplRecover> TplRecoverMapPair;
    typedef std::map<std::string, TplRecover> TplRecoverMap;
    typedef TplRecoverMap::const_iterator TplRecoverMapCitr;

    typedef std::map<std::string, JournalImpl*> JournalListMap;
    typedef JournalListMap::iterator JournalListMapItr;

    // Default store settings
    static const bool defTruncateFlag = false;
    static const uint32_t defWCachePageSize = JRNL_WMGR_DEF_PAGE_SIZE * JRNL_SBLK_SIZE / 1024;
    static const uint32_t defTplWCachePageSize = defWCachePageSize / 8;
    static const uint16_t defEfpPartition = 0;
    static const uint64_t defEfpFileSize = 512 * JRNL_SBLK_SIZE;

    static const std::string storeTopLevelDir;
    static qpid::sys::Duration defJournalGetEventsTimeout;
    static qpid::sys::Duration defJournalFlushTimeout;

    std::list<db_ptr> dbs;
    dbEnv_ptr dbenv;
    db_ptr queueDb;
    db_ptr configDb;
    db_ptr exchangeDb;
    db_ptr mappingDb;
    db_ptr bindingDb;
    db_ptr generalDb;

    // Pointer to Transaction Prepared List (TPL) journal instance
    boost::shared_ptr<TplJournalImpl> tplStorePtr;
    TplRecoverMap tplRecoverMap;
    qpid::sys::Mutex tplInitLock;
    JournalListMap journalList;
    qpid::sys::Mutex journalListLock;
    qpid::sys::Mutex bdbLock;

    IdSequence queueIdSequence;
    IdSequence exchangeIdSequence;
    IdSequence generalIdSequence;
    IdSequence messageIdSequence;
    std::string storeDir;
    uint16_t numJrnlFiles;
    bool      autoJrnlExpand;
    uint16_t autoJrnlExpandMaxFiles;
    uint32_t jrnlFsizeSblks;
    bool      truncateFlag;
    uint32_t wCachePgSizeSblks;
    uint16_t wCacheNumPages;
    uint16_t tplNumJrnlFiles;
    uint32_t tplJrnlFsizeSblks;
    uint32_t tplWCachePgSizeSblks;
    uint16_t tplWCacheNumPages;
    uint64_t highestRid;
    bool isInit;
    const char* envPath;
    qpid::broker::Broker* broker;

    qmf::org::apache::qpid::linearstore::Store::shared_ptr mgmtObject;
    qpid::management::ManagementAgent* agent;


    // Parameter validation and calculation
    static uint32_t chkJrnlWrPageCacheSize(const uint32_t param,
                                            const std::string paramName/*,
                                            const uint16_t jrnlFsizePgs*/);
    static uint16_t getJrnlWrNumPages(const uint32_t wrPageSizeKib);

    void init();

    void recoverQueues(TxnCtxt& txn,
                       qpid::broker::RecoveryManager& recovery,
                       queue_index& index,
                       txn_list& locked,
                       message_index& messages);
    void recoverMessages(TxnCtxt& txn,
                         qpid::broker::RecoveryManager& recovery,
                         queue_index& index,
                         txn_list& locked,
                         message_index& prepared);
    void recoverMessages(TxnCtxt& txn,
                         qpid::broker::RecoveryManager& recovery,
                         qpid::broker::RecoverableQueue::shared_ptr& queue,
                         txn_list& locked,
                         message_index& prepared,
                         long& rcnt,
                         long& idcnt);
    qpid::broker::RecoverableMessage::shared_ptr getExternMessage(qpid::broker::RecoveryManager& recovery,
                                                                  uint64_t mId,
                                                                  unsigned& headerSize);
    void recoverExchanges(TxnCtxt& txn,
                          qpid::broker::RecoveryManager& recovery,
                          exchange_index& index);
    void recoverBindings(TxnCtxt& txn,
                         exchange_index& exchanges,
                         queue_index& queues);
    void recoverGeneral(TxnCtxt& txn,
                        qpid::broker::RecoveryManager& recovery);
    int enqueueMessage(TxnCtxt& txn,
                       IdDbt& msgId,
                       qpid::broker::RecoverableMessage::shared_ptr& msg,
                       queue_index& index,
                       txn_list& locked,
                       message_index& prepared);
    void readTplStore();
    void recoverTplStore();
    void recoverLockedMappings(txn_list& txns);
    TxnCtxt* check(qpid::broker::TransactionContext* ctxt);
    uint64_t msgEncode(std::vector<char>& buff, const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message);
    void store(const qpid::broker::PersistableQueue* queue,
               TxnCtxt* txn,
               const boost::intrusive_ptr<qpid::broker::PersistableMessage>& message,
               bool newId);
    void async_dequeue(qpid::broker::TransactionContext* ctxt,
                       const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                       const qpid::broker::PersistableQueue& queue);
    void destroy(db_ptr db,
                 const qpid::broker::Persistable& p);
    bool create(db_ptr db,
                IdSequence& seq,
                const qpid::broker::Persistable& p);
    void completed(TxnCtxt& txn,
                   bool commit);
    void deleteBindingsForQueue(const qpid::broker::PersistableQueue& queue);
    void deleteBinding(const qpid::broker::PersistableExchange& exchange,
                       const qpid::broker::PersistableQueue& queue,
                       const std::string& key);

    void put(db_ptr db,
             DbTxn* txn,
             Dbt& key,
             Dbt& value);
    void open(db_ptr db,
              DbTxn* txn,
              const char* file,
              bool dupKey);
    void closeDbs();

    // journal functions
    void createJrnlQueue(const qpid::broker::PersistableQueue& queue);
    uint32_t bHash(const std::string str);
    std::string getJrnlDir(const qpid::broker::PersistableQueue& queue); //for exmaple /var/rhm/ + queueDir/
    std::string getJrnlHashDir(const std::string& queueName);
    std::string getJrnlBaseDir();
    std::string getBdbBaseDir();
    std::string getTplBaseDir();
    inline void checkInit() {
        // TODO: change the default dir to ~/.qpidd
        if (!isInit) { init("/tmp"); isInit = true; }
    }
    void chkTplStoreInit();

    // debug aid for printing XIDs that may contain non-printable chars
    static std::string xid2str(const std::string xid) {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        for (unsigned i=0; i<xid.size(); i++) {
            if (isprint(xid[i]))
                oss << xid[i];
            else
                oss << "/" << std::setw(2) << (int)((char)xid[i]);
        }
        return oss.str();
    }

  public:
    typedef boost::shared_ptr<MessageStoreImpl> shared_ptr;

    MessageStoreImpl(qpid::broker::Broker* broker, const char* envpath = 0);

    virtual ~MessageStoreImpl();

    bool init(const qpid::Options* options);

    bool init(const std::string& dir,
              const bool truncateFlag = false,
              uint32_t wCachePageSize = defWCachePageSize,
              uint32_t tplWCachePageSize = defTplWCachePageSize);

    void truncateInit(const bool saveStoreContent = false);

    void initManagement ();

    void finalize();

    void create(qpid::broker::PersistableQueue& queue,
                const qpid::framing::FieldTable& args);

    void destroy(qpid::broker::PersistableQueue& queue);

    void create(const qpid::broker::PersistableExchange& queue,
                const qpid::framing::FieldTable& args);

    void destroy(const qpid::broker::PersistableExchange& queue);

    void bind(const qpid::broker::PersistableExchange& exchange,
              const qpid::broker::PersistableQueue& queue,
              const std::string& key,
              const qpid::framing::FieldTable& args);

    void unbind(const qpid::broker::PersistableExchange& exchange,
                const qpid::broker::PersistableQueue& queue,
                const std::string& key,
                const qpid::framing::FieldTable& args);

    void create(const qpid::broker::PersistableConfig& config);

    void destroy(const qpid::broker::PersistableConfig& config);

    void recover(qpid::broker::RecoveryManager& queues);

    void stage(const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg);

    void destroy(qpid::broker::PersistableMessage& msg);

    void appendContent(const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& msg,
                       const std::string& data);

    void loadContent(const qpid::broker::PersistableQueue& queue,
                     const boost::intrusive_ptr<const qpid::broker::PersistableMessage>& msg,
                     std::string& data,
                     uint64_t offset,
                     uint32_t length);

    void enqueue(qpid::broker::TransactionContext* ctxt,
                 const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                 const qpid::broker::PersistableQueue& queue);

    void dequeue(qpid::broker::TransactionContext* ctxt,
                 const boost::intrusive_ptr<qpid::broker::PersistableMessage>& msg,
                 const qpid::broker::PersistableQueue& queue);

    void flush(const qpid::broker::PersistableQueue& queue);

    uint32_t outstandingQueueAIO(const qpid::broker::PersistableQueue& queue);

    void collectPreparedXids(std::set<std::string>& xids);

    std::auto_ptr<qpid::broker::TransactionContext> begin();

    std::auto_ptr<qpid::broker::TPCTransactionContext> begin(const std::string& xid);

    void prepare(qpid::broker::TPCTransactionContext& ctxt);

    void localPrepare(TxnCtxt* ctxt);

    void commit(qpid::broker::TransactionContext& ctxt);

    void abort(qpid::broker::TransactionContext& ctxt);

    qpid::management::ManagementObject::shared_ptr GetManagementObject (void) const
        { return mgmtObject; }

    inline qpid::management::Manageable::status_t ManagementMethod (uint32_t, qpid::management::Args&, std::string&)
        { return qpid::management::Manageable::STATUS_OK; }

    std::string getStoreDir() const;

  private:
    void journalDeleted(JournalImpl&);

}; // class MessageStoreImpl

} // namespace msgstore
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_MESSAGESTOREIMPL_H
