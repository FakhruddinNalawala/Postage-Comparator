package com.postage.postagecomparator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Item;
import com.postage.postagecomparator.util.FileWriteUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;

@Service
public class ItemServiceImpl implements ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemServiceImpl.class);

    private static final String DEFAULT_DIR_NAME = ".postage-comparator";
    private static final String ITEMS_FILE_NAME = "items.json";

    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    public ItemServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Item> findAll() {
        return listAllItems();
    }

    @Override
    public Optional<Item> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id must not be null or blank");
        }
        var items = listAllItems();
        return items.stream()
                .filter(item -> item.id().equals(id))
                .findFirst();
    }

    @Override
    public Item create(Item item) {
        if (item == null) {
            throw new BadRequestException("Item must not be null");
        }
        if (item.name() == null || item.name().isBlank()) {
            throw new BadRequestException("Item name is required");
        }
        if (item.unitWeightGrams() <= 0) {
            throw new BadRequestException("Item unit weight must be greater than 0");
        }
        var items = listAllItems();
        if (items.stream().anyMatch(checkItem -> checkItem.name().equals(item.name()))) {
            throw new BadRequestException("Item with name " + item.name() + " already exists");
        }
        var newItem = new Item(generateItemId(), item.name(), item.description(), item.unitWeightGrams());
        items.add(newItem);
        saveItems(items);
        return newItem;
    }

    @Override
    public Item update(String id, Item item) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("id must not be null or blank");
        }
        if (item == null) {
            throw new BadRequestException("item must not be null");
        }

        var items = listAllItems();
        if (item.name() != null && !item.name().isBlank()) {
            var duplicateExists = items.stream()
                    .anyMatch(checkItem -> checkItem.name().equals(item.name()) 
                            && !checkItem.id().equals(id));
            if (duplicateExists) {
                throw new BadRequestException("Item with name " + item.name() + " already exists");
            }
        }

        var existingIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).id())) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex == -1) {
            throw new NotFoundException("Item with id " + id + " not found");
        }

        var existing = items.get(existingIndex);
        var updated = new Item(
                existing.id(),
                item.name() != null && !item.name().isBlank() ? item.name() : existing.name(),
                item.description() != null ? item.description() : existing.description(),
                item.unitWeightGrams() > 0 ? item.unitWeightGrams() : existing.unitWeightGrams()
        );
        items.set(existingIndex, updated);
        saveItems(items);
        return updated;
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        var items = listAllItems();
        if (items.isEmpty()) {
            return;
        }
        var filteredItems = items.stream()
                .filter(item -> !item.id().equals(id))
                .toList();
        saveItems(filteredItems);
    }

    private Path itemsPath() {
        var baseDir = System.getProperty("POSTAGE_DATA_DIR");
        if (baseDir == null || baseDir.isBlank()) {
            baseDir = System.getenv("POSTAGE_DATA_DIR");
        }
        if (baseDir == null || baseDir.isBlank()) {
            var userHome = System.getProperty("user.home");
            baseDir = Path.of(userHome, DEFAULT_DIR_NAME).toString();
        }
        return Path.of(baseDir, ITEMS_FILE_NAME);
    }

    private List<Item> listAllItems() {
        var path = itemsPath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        synchronized (lock) {
            try {
                var items = objectMapper.readValue(path.toFile(), new TypeReference<List<Item>>() {});
                return items != null ? new ArrayList<>(items) : new ArrayList<>();
            } catch (IOException e) {
                log.error("Failed to read items from {}", path, e);
                throw new IllegalStateException("Unable to read items", e);
            }
        }
    }

    private String generateItemId() {
        return UUID.randomUUID().toString();
    }

    private void saveItems(List<Item> items) {
        synchronized (lock) {
            var path = itemsPath();
            FileWriteUtils.safeWrite(path, temp -> {
                try {
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValue(temp.toFile(), items);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to write items to " + path, e);
                }
            }, log);
        }
    }
}
