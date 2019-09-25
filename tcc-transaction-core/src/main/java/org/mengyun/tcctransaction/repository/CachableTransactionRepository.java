package org.mengyun.tcctransaction.repository;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.mengyun.tcctransaction.ConcurrentTransactionException;
import org.mengyun.tcctransaction.OptimisticLockException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.api.TransactionXid;

import javax.transaction.xa.Xid;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by changmingxie on 10/30/15.
 */
public abstract class CachableTransactionRepository implements TransactionRepository {
    /**
     * 缓存过期时间
     */
    private int expireDuration = 120;
    /**
     * 缓存
     */
    private Cache<Xid, Transaction> transactionXidCompensableTransactionCache;

    //存储事务
    @Override
    public int create(Transaction transaction) {
        int result = doCreate(transaction);
        if (result > 0) {
            putToCache(transaction);
        } else {
            throw new ConcurrentTransactionException("transaction xid duplicated. xid:" + transaction.getXid().toString());
        }

        return result;
    }

    @Override
    public int update(Transaction transaction) {
        int result = 0;

        try {
            result = doUpdate(transaction);
            if (result > 0) {
                putToCache(transaction);
            } else {
                throw new OptimisticLockException();
            }
        } finally {
            if (result <= 0) {// 更新失败，移除缓存。下次访问，从存储器读取
                removeFromCache(transaction);
            }
        }

        return result;
    }

    @Override
    public int delete(Transaction transaction) {
        int result = 0;

        try {
            result = doDelete(transaction);

        } finally {
            removeFromCache(transaction);
        }
        return result;
    }

    @Override
    public Transaction findByXid(TransactionXid transactionXid) {
        Transaction transaction = findFromCache(transactionXid);

        if (transaction == null) {
            transaction = doFindOne(transactionXid);

            if (transaction != null) {
                putToCache(transaction);
            }
        }

        return transaction;
    }

    @Override
    public List<Transaction> findAllUnmodifiedSince(Date date) {

        List<Transaction> transactions = doFindAllUnmodifiedSince(date);
        // 添加到缓存
        for (Transaction transaction : transactions) {
            putToCache(transaction);
        }

        return transactions;
    }

    public CachableTransactionRepository() {
        transactionXidCompensableTransactionCache = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, TimeUnit.SECONDS).maximumSize(1000).build();
    }

    /**
     * 添加到缓存
     *
     * @param transaction 事务
     */
    protected void putToCache(Transaction transaction) {
        transactionXidCompensableTransactionCache.put(transaction.getXid(), transaction);
    }
    /**
     * 移除事务从缓存
     *
     * @param transaction 事务
     */
    protected void removeFromCache(Transaction transaction) {
        transactionXidCompensableTransactionCache.invalidate(transaction.getXid());
    }
    /**
     * 获得事务从缓存中
     *
     * @param transactionXid 事务编号
     * @return 事务
     */
    protected Transaction findFromCache(TransactionXid transactionXid) {
        return transactionXidCompensableTransactionCache.getIfPresent(transactionXid);
    }

    public void setExpireDuration(int durationInSeconds) {
        this.expireDuration = durationInSeconds;
    }
    /**
     * 新增事务
     *
     * @param transaction 事务
     * @return 新增数量
     */
    protected abstract int doCreate(Transaction transaction);
    /**
     * 更新事务
     *
     * @param transaction 事务
     * @return 新增数量
     */
    protected abstract int doUpdate(Transaction transaction);
    /**
     * 更新事务
     *
     * @param transaction 事务
     * @return 删除数量
     */
    protected abstract int doDelete(Transaction transaction);
    /**
     * 查询事务
     *
     * @param xid 事务编号
     * @return 事务
     */
    protected abstract Transaction doFindOne(Xid xid);
    /**
     * 获取超过指定时间的事务集合
     *
     * @param date 指定时间
     * @return 事务集合
     */
    protected abstract List<Transaction> doFindAllUnmodifiedSince(Date date);
}
