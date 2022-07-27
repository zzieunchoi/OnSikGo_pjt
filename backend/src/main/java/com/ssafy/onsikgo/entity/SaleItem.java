package com.ssafy.onsikgo.entity;

import com.ssafy.onsikgo.dto.ItemDto;
import com.ssafy.onsikgo.dto.SaleDto;
import com.ssafy.onsikgo.dto.SaleItemDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

import static javax.persistence.FetchType.LAZY;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SaleItem {

    @Id @GeneratedValue
    private Long saleItemId;

    @Column(nullable = false)
    private Integer stock; // 재고

    @Column(nullable = false)
    private Integer totalStock; // 총 재고

    @Column(nullable = false)
    private Integer salePrice; // 할인가

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "saleId")
    private Sale sale;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "itemId")
    private Item item;

    @OneToMany(mappedBy = "saleItem")
    private List<Order> orders = new ArrayList<>();

    public SaleItemDto toDto(ItemDto itemDto, SaleDto saleDto) {
        return SaleItemDto.builder()
                .stock(this.stock)
                .totalStock(this.totalStock)
                .salePrice(this.salePrice)
                .saleDto(saleDto)
                .itemDto(itemDto)
                .itemId(itemDto.getItemId())
                .build();
    }

    public SaleItem update(Integer stock) {
        this.stock = stock;
        return this;
    }
}
