package com.postage.postagecomparator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemServiceImplTest {

    @TempDir
    Path tempDir;

    private ItemServiceImpl itemService;

    @BeforeEach
    void setUp() {
        // Point the default data directory under a temporary user.home
        System.setProperty("user.home", tempDir.toString());

        itemService = new ItemServiceImpl(new ObjectMapper());
    }

    @Test
    void findAll_whenNoItems_returnsEmptyList() {
        var all = itemService.findAll();
        assertThat(all).isEmpty();
    }

    @Test
    void findAll_afterInsert_returnsSingleItem() {
        itemService.create(new Item(null, "Box", "Small box", 100));

        var all = itemService.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().name()).isEqualTo("Box");
    }

    @Test
    void create_whenItemIsNull_throwsBadRequestException() {
        assertThatThrownBy(() -> itemService.create(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Item must not be null");
    }

    @Test
    void create_whenValidItem_persistsAndReturnsItem() throws Exception {
        var created = itemService.create(new Item(null, "Box", "Small box", 100));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Box");
        assertThat(created.description()).isEqualTo("Small box");
        assertThat(created.unitWeightGrams()).isEqualTo(100);

        // Verify it was written to disk and can be read via findAll
        List<Item> all = itemService.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().name()).isEqualTo("Box");

        // Also verify the file actually exists
        Path itemsFile = tempDir
                .resolve(".postage-comparator")
                .resolve("items.json");
        assertThat(Files.exists(itemsFile)).isTrue();
    }

    @Test
    void create_whenNameMissing_throwsBadRequestException() {
        var withoutName = new Item(null, null, "Desc", 100);

        assertThatThrownBy(() -> itemService.create(withoutName))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Item name is required");
    }

    @Test
    void create_whenNameBlank_throwsBadRequestException() {
        var withBlankName = new Item(null, "   ", "Desc", 100);

        assertThatThrownBy(() -> itemService.create(withBlankName))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Item name is required");
    }

    @Test
    void create_whenDescriptionMissing_succeeds() {
        var created = itemService.create(new Item(null, "Box", null, 100));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Box");
        assertThat(created.description()).isNull();
        assertThat(created.unitWeightGrams()).isEqualTo(100);
    }

    @Test
    void create_whenUnitWeightMissingOrNonPositive_throwsBadRequestException() {
        var zeroWeight = new Item(null, "Box", "Desc", 0);
        var negativeWeight = new Item(null, "Box", "Desc", -10);

        assertThatThrownBy(() -> itemService.create(zeroWeight))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be greater than 0");
        assertThatThrownBy(() -> itemService.create(negativeWeight))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be greater than 0");
    }

    @Test
    void create_whenCustomIdProvided_isIgnored() {
        var created = itemService.create(new Item("custom-id", "Box", "Desc", 100));

        assertThat(created.id()).isNotNull();
        assertThat(created.id()).isNotEqualTo("custom-id");
    }

    @Test
    void create_thenFindById_returnsItem() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        var found = itemService.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().name()).isEqualTo("Box");
    }

    @Test
    void create_whenDuplicateName_throwsBadRequestException() {
        itemService.create(new Item(null, "Box", "First", 100));

        assertThatThrownBy(() ->
                itemService.create(new Item(null, "Box", "Second", 200))
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findById_whenSpuriousId_returnsEmptyOptional() {
        var result = itemService.findById("non-existent-id");
        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenUsingNameInsteadOfId_returnsEmptyOptional() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        var result = itemService.findById(created.name());
        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenIdIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> itemService.findById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id must not be null or blank");
    }

    @Test
    void findById_whenIdBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> itemService.findById("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id must not be null or blank");
    }

    @Test
    void update_whenIdMissing_throwsBadRequestException() {
        var item = new Item(null, "Box", "Desc", 100);

        assertThatThrownBy(() -> itemService.update(null, item))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void update_whenItemBodyMissing_throwsBadRequestException() {
        assertThatThrownBy(() -> itemService.update("some-id", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("item must not be null");
    }

    @Test
    void update_whenIdDoesNotExist_throwsNotFoundException() {
        var item = new Item(null, "Box", "Desc", 100);

        assertThatThrownBy(() -> itemService.update("non-existent-id", item))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void update_whenRenamingToExistingName_throwsBadRequestException() {
        itemService.create(new Item(null, "Box1", "Desc1", 100));
        var item2 = itemService.create(new Item(null, "Box2", "Desc2", 200));

        var updateItem2 = new Item(null, "Box1", "New desc", 200);

        assertThatThrownBy(() -> itemService.update(item2.id(), updateItem2))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_withoutName_keepsExistingName() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        var update = new Item(null, null, "New desc", 0);
        var updated = itemService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("Box");
        assertThat(updated.description()).isEqualTo("New desc");
        assertThat(updated.unitWeightGrams()).isEqualTo(100);
    }

    @Test
    void update_withoutDescription_keepsExistingDescription() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        var update = new Item(null, "New name", null, 150);
        var updated = itemService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.description()).isEqualTo("Desc");
        assertThat(updated.unitWeightGrams()).isEqualTo(150);
    }

    @Test
    void update_withoutUnitWeight_keepsExistingWeight() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        var update = new Item(null, "New name", "New desc", 0);
        var updated = itemService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.description()).isEqualTo("New desc");
        assertThat(updated.unitWeightGrams()).isEqualTo(100);
    }

    @Test
    void delete_whenIdMissing_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> itemService.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void delete_whenIdBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> itemService.delete("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void delete_whenIdDoesNotExist_doesNothing() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));

        itemService.delete("non-existent-id");

        var all = itemService.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().id()).isEqualTo(created.id());
    }

    @Test
    void delete_whenExistingItem_thenFindAllReturnsEmpty() {
        var created = itemService.create(new Item(null, "Box", "Desc", 100));
        assertThat(itemService.findAll()).hasSize(1);

        itemService.delete(created.id());

        var all = itemService.findAll();
        assertThat(all).isEmpty();
    }

    @Test
    void findAll_whenItemsFileCorrupted_throwsIllegalStateException() throws Exception {
        // Arrange: write invalid JSON to the expected items.json location
        Path itemsFile = tempDir
                .resolve(".postage-comparator")
                .resolve("items.json");
        Files.createDirectories(itemsFile.getParent());
        Files.writeString(itemsFile, "not valid json");

        // Act + Assert
        assertThatThrownBy(() -> itemService.findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to read items");
    }
}
