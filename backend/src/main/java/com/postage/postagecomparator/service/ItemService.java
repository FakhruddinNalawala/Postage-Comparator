package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.Item;

import java.util.List;
import java.util.Optional;

public interface ItemService {

    List<Item> findAll();

    Optional<Item> findById(String id);

    Item create(Item item);

    Item update(String id, Item item);

    void delete(String id);
}
