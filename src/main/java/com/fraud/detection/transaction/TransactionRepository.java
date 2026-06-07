package com.fraud.detection.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fraud.detection.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}
