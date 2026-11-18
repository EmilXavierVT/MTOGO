package app.entities.restaurant;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
public class Product {
    public static final int NAME_MAX_LENGTH = 255;
    public static final int PRICE_PRECISION = 12;
    public static final int PRICE_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description_da", columnDefinition = "text")
    private String descriptionDa;

    @Column(name = "name_en", length = NAME_MAX_LENGTH)
    private String nameEn;

    @Column(name = "description_en", columnDefinition = "text")
    private String descriptionEn;

    @Column(nullable = false, precision = PRICE_PRECISION, scale = PRICE_SCALE)
    private BigDecimal price;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean available = true;

    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;
}
