package org.mengyun.tcctransaction.common;

/**
 * Created by changmingxie on 11/11/15.
 * 可以根据不同方法类型，做不同的事务处理。
 */
public enum MethodRole {
    // 调用 #rootMethodProceed(...) 方法，发起 TCC 整体流程
    ROOT,
    // 项目已经不再使用，猜测已废弃
    CONSUMER,
    // 服务提供者参与 TCC 整体流程
    PROVIDER,
    // 不进行事务处理。
    NORMAL;
}
