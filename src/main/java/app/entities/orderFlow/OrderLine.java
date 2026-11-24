package app.entities.orderFlow;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

import java.util.UUID;

    @Entity
    @Getter
    @Setter
    @NoArgsConstructor
    public class OrderLine {
        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "order_id", nullable = false)
        private Order order;

        private UUID productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
