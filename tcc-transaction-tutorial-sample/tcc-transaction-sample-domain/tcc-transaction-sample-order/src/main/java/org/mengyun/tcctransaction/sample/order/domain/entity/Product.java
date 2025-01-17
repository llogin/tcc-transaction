package org.mengyun.tcctransaction.sample.order.domain.entity;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Created by twinkle.zhou on 16/11/10.
 * 商品表
 */
public class Product implements Serializable{
    /**
     * 商品编号
     */
    private long productId;
    /**
     * 商店编号
     */
    private long shopId;
    /**
     * 商品名
     */
    private String productName;
    /**
     * 单价
     */
    private BigDecimal price;

    public Product() {
    }

    public Product(long productId, long shopId, String productName, BigDecimal price) {
        this.productId = productId;
        this.shopId = shopId;
        this.productName = productName;
        this.price = price;
    }

    public long getProductId() {
        return productId;
    }

    public long getShopId() {
        return shopId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
