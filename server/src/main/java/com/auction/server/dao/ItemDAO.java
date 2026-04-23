package com.auction.server.dao;

import com.auction.shared.model.item.Item;
import com.auction.shared.enums.ItemCategory;
import java.util.List;

public interface ItemDAO {
    Item findById(String id);
    List<Item> findBySellerId(String sellerId);
    List<Item> findByCategory(ItemCategory category);

    List<Item> searchByName(String keyword);

    boolean save(Item item);
    boolean update(Item item);
    boolean delete(String id);
}
