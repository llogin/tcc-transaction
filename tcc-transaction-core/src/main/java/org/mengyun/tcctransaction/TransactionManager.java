package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

/**
 * Created by changmingxie on 10/26/15.
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());

    private TransactionRepository transactionRepository;

    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<Deque<Transaction>>();

    private ExecutorService executorService;

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public TransactionManager() {


    }
    /**
     * 发起根事务
     *
     * @return 事务
     */
    public Transaction begin(Object uniqueIdentify) {
        // 创建 根事务
        Transaction transaction = new Transaction(uniqueIdentify,TransactionType.ROOT);
        // 存储 事务
        transactionRepository.create(transaction);
        // 注册 事务
        registerTransaction(transaction);
        return transaction;
    }
    /**
     * 发起根事务
     *
     * @return 事务
     */
    public Transaction begin() {
        // 创建 根事务
        Transaction transaction = new Transaction(TransactionType.ROOT);
        // 存储 事务
        transactionRepository.create(transaction);
        // 注册 事务
        registerTransaction(transaction);
        return transaction;
    }
    /**
     * 传播发起分支事务
     *
     * @param transactionContext 事务上下文
     * @return 分支事务
     */
    public Transaction propagationNewBegin(TransactionContext transactionContext) {
        // 创建 分支事务
        Transaction transaction = new Transaction(transactionContext);
        // 存储 事务
        transactionRepository.create(transaction);
        // 注册 事务
        registerTransaction(transaction);
        return transaction;
    }
    /**
     * 传播获取分支事务
     *
     * @param transactionContext 事务上下文
     * @return 分支事务
     * @throws NoExistedTransactionException 当事务不存在时
     */
    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        // 查询 事务
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());

        if (transaction != null) {
            // 设置 事务 状态
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            // 注册 事务
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }
    /**
     * 提交事务
     */
    public void commit(boolean asyncCommit) {
        // 获取 事务
        final Transaction transaction = getCurrentTransaction();
        // 设置 事务状态 为 CONFIRMING
        transaction.changeStatus(TransactionStatus.CONFIRMING);
        // 更新 事务
        transactionRepository.update(transaction);

        if (asyncCommit) {
            try {
                Long statTime = System.currentTimeMillis();

                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        commitTransaction(transaction);
                    }
                });
                logger.debug("async submit cost time:" + (System.currentTimeMillis() - statTime));
            } catch (Throwable commitException) {
                logger.warn("compensable transaction async submit confirm failed, recovery job will try to confirm later.", commitException);
                throw new ConfirmingException(commitException);
            }
        } else {
            commitTransaction(transaction);
        }
    }
    /**
     * 回滚事务
     */
    public void rollback(boolean asyncRollback) {
        // 获取 事务
        final Transaction transaction = getCurrentTransaction();
        // 设置 事务状态 为 CANCELLING
        transaction.changeStatus(TransactionStatus.CANCELLING);
        // 更新 事务
        transactionRepository.update(transaction);

        if (asyncRollback) {

            try {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        rollbackTransaction(transaction);
                    }
                });
            } catch (Throwable rollbackException) {
                logger.warn("compensable transaction async rollback failed, recovery job will try to rollback later.", rollbackException);
                throw new CancellingException(rollbackException);
            }
        } else {

            rollbackTransaction(transaction);
        }
    }


    private void commitTransaction(Transaction transaction) {
        try {
            // 提交 事务
            transaction.commit();
            // 删除 事务
            transactionRepository.delete(transaction);
        } catch (Throwable commitException) {
            logger.warn("compensable transaction confirm failed, recovery job will try to confirm later.", commitException);
            throw new ConfirmingException(commitException);
        }
    }

    private void rollbackTransaction(Transaction transaction) {
        try {
            // 回滚 事务
            transaction.rollback();
            // 删除 事务
            transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {
            logger.warn("compensable transaction rollback failed, recovery job will try to rollback later.", rollbackException);
            throw new CancellingException(rollbackException);
        }
    }

    public Transaction getCurrentTransaction() {
        if (isTransactionActive()) {
            return CURRENT.get().peek();// 获得头部元素
        }
        return null;
    }

    public boolean isTransactionActive() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * 注册事务到当前线程事务队列
     *
     * @param transaction 事务
     */
    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<Transaction>());
        }
        // 添加到头部
        CURRENT.get().push(transaction);
    }

    public static void main(String[] args) {
        LinkedList list = new LinkedList<String>();
        list.push("a");
        list.push("b");
        list.push("c");
        System.out.println(list);
        System.out.println(list.peek());
        System.out.println(list.peek());
        System.out.println(list.peek());
        System.out.println(list.pop());
        System.out.println(list.pop());

    }

    public void cleanAfterCompletion(Transaction transaction) {
        if (isTransactionActive() && transaction != null) {
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                CURRENT.get().pop();
                if (CURRENT.get().size() == 0) {
                    CURRENT.remove();
                }
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }

    public void enlistParticipant(Participant participant) {
        // 获取 事务
        Transaction transaction = this.getCurrentTransaction();
        // 添加参与者到事务
        transaction.enlistParticipant(participant);
        // 更新 事务
        transactionRepository.update(transaction);
    }
}
