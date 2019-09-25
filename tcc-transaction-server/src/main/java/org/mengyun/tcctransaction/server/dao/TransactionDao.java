package org.mengyun.tcctransaction.server.dao;

import org.mengyun.tcctransaction.server.dto.PageDto;
import org.mengyun.tcctransaction.server.vo.TransactionVo;

import java.util.List;

/**
 * Created by changming.xie on 9/7/16.
 */
public interface TransactionDao {

    void confirm(String globalTxId, String branchQualifier);

    void cancel(String globalTxId, String branchQualifier);

    void delete(String globalTxId, String branchQualifier);

    void restore(String globalTxId, String branchQualifier);
    /**
     * 重置事务重试次数
     *
     * @param globalTxId 全局事务编号
     * @param branchQualifier 分支事务编号
     * @return 是否重置成功
     */
    void resetRetryCount(String globalTxId, String branchQualifier);

    String getDomain();
    /**
     * 获得事务 VO 数组
     *
     * @param pageNum 第几页
     * @param pageSize 分页大小
     * @return 事务 VO 数组
     */
    PageDto<TransactionVo> findTransactions(Integer pageNum, int pageSize);

    PageDto<TransactionVo> findDeletedTransactions(Integer pageNum, int pageSize);
}

