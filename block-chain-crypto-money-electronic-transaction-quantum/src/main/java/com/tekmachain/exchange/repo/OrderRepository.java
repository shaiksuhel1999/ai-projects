package com.tekmachain.exchange.repo;

import com.tekmachain.exchange.domain.Order;
import com.tekmachain.exchange.domain.OrderStatus;
import com.tekmachain.exchange.domain.Side;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Best asks first: lowest price, then oldest. */
    List<Order> findByPairAndSideAndStatusInOrderByPriceAscCreatedAtAsc(
            String pair, Side side, List<OrderStatus> statuses);

    /** Best bids first: highest price, then oldest. */
    List<Order> findByPairAndSideAndStatusInOrderByPriceDescCreatedAtAsc(
            String pair, Side side, List<OrderStatus> statuses);

    List<Order> findByPairAndStatusIn(String pair, List<OrderStatus> statuses);

    List<Order> findByAddressOrderByCreatedAtDesc(String address);
}
