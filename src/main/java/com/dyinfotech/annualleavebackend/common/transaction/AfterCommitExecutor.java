package com.dyinfotech.annualleavebackend.common.transaction;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 모든 서비스에서 주입받아 사용하는 트랜잭션 커밋 후 실행기. */
@Component
public class AfterCommitExecutor {

    public void execute(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("활성 트랜잭션이 필요합니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
