package com.auction.server.dao;

import com.auction.shared.model.item.Item;
import com.auction.shared.enums.ItemCategory;
import java.util.List;

public interface ItemDAO {
    Item findById(int id);
    List<Item> findBySellerId(int sellerId);
    List<Item> findByCategory(ItemCategory category);

    List<Item> searchByName(String keyword);

    boolean save(Item item);
    boolean update(Item item);
    boolean delete(int id);
}
