package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.UniqueIdentity;
import org.mengyun.tcctransaction.common.MethodRole;
import org.mengyun.tcctransaction.support.FactoryBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Created by changming.xie on 04/04/19.
 */
public class CompensableMethodContext {

    ProceedingJoinPoint pjp = null;

    Method method = null;

    Compensable compensable = null;

    Propagation propagation = null;

    TransactionContext transactionContext = null;

    public CompensableMethodContext(ProceedingJoinPoint pjp) {
        this.pjp = pjp;
        // 获得带 @Compensable 注解的方法
        this.method = getCompensableMethod();
        // 获取Compensable注解
        this.compensable = method.getAnnotation(Compensable.class);
        // 获取传播级别
        this.propagation = compensable.propagation();
        // 获得 事务上下文
        this.transactionContext = FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs());

    }

    public Compensable getAnnotation() {
        return compensable;
    }

    public Propagation getPropagation() {
        return propagation;
    }

    public TransactionContext getTransactionContext() {
        return transactionContext;
    }

    public Method getMethod() {
        return method;
    }

    public Object getUniqueIdentity() {
        Annotation[][] annotations = this.getMethod().getParameterAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation.annotationType().equals(UniqueIdentity.class)) {

                    Object[] params = pjp.getArgs();
                    Object unqiueIdentity = params[i];

                    return unqiueIdentity;
                }
            }
        }

        return null;
    }


    private Method getCompensableMethod() {
        Method method = ((MethodSignature) (pjp.getSignature())).getMethod();

        if (method.getAnnotation(Compensable.class) == null) {
            try {
                method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        return method;
    }
    /**
     * 获取方法类型
     *
     * @param isTransactionActive 事务是否已开启
     * @return 方法类型
     */
    public MethodRole getMethodRole(boolean isTransactionActive) {
        if ((propagation.equals(Propagation.REQUIRED) && !isTransactionActive && transactionContext == null) // Propagation.REQUIRED：支持当前事务，当前没有事务，就新建一个事务。
                ||
                propagation.equals(Propagation.REQUIRES_NEW)) { // Propagation.REQUIRES_NEW：新建事务，如果当前存在事务，把当前事务挂起。
            return MethodRole.ROOT;
        } else if ((propagation.equals(Propagation.REQUIRED)// Propagation.REQUIRED：支持当前事务
                || propagation.equals(Propagation.MANDATORY))  // Propagation.MANDATORY：支持当前事务
                && !isTransactionActive && transactionContext != null) {
            return MethodRole.PROVIDER;
        } else {
            return MethodRole.NORMAL;
        }
    }

    public Object proceed() throws Throwable {
        return this.pjp.proceed();
    }
}