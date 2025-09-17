package com.akiramenai.videobackend.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.akiramenai.videobackend.model.Purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRepo extends JpaRepository<Purchase, UUID> {
  Optional<Purchase> findPurchaseById(UUID id);

  Optional<Purchase> findPurchaseByAuthorId(UUID authorId);

  Page<Purchase> findPurchaseByBuyerId(UUID buyerId, Pageable pageable);

  long countByAuthorIdAndPurchaseTimestampBetween(UUID authorId, LocalDateTime purchaseTimestampLdtAfter, LocalDateTime purchaseTimestampLdtBefore);

  List<Purchase> findAllByAuthorIdAndPurchaseTimestampBetween(UUID authorId, LocalDateTime purchaseTimestampLdtAfter, LocalDateTime purchaseTimestampLdtBefore);

  List<Purchase> findAllByAuthorIdAndPurchaseDate(UUID authorId, LocalDate purchaseDate);
}
