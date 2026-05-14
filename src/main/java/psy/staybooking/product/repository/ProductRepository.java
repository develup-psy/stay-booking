package psy.staybooking.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import psy.staybooking.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
