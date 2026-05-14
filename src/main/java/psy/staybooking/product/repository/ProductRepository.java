package psy.staybooking.product.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import psy.staybooking.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from Product product where product.productId = :productId")
    Optional<Product> findByProductIdForUpdate(Long productId);
}
