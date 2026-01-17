package com.postage.postagecomparator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Packaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackagingServiceImplTest {

    @TempDir
    Path tempDir;

    private PackagingServiceImpl packagingService;

    @BeforeEach
    void setUp() {
        // Use a temp user.home so the JSON file is isolated per test
        System.setProperty("user.home", tempDir.toString());
        packagingService = new PackagingServiceImpl(new ObjectMapper());
    }

    @Test
    void findAll_whenNoPackagings_returnsEmptyList() {
        var all = packagingService.findAll();
        assertThat(all).isEmpty();
    }

    @Test
    void findAll_afterInsert_returnsSinglePackaging() {
        packagingService.create(new Packaging(null, "Small Box", "Desc",
                10, 20, 30, 0, 1.5));

        var all = packagingService.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().name()).isEqualTo("Small Box");
    }

    @Test
    void create_whenPackagingIsNull_throwsBadRequestException() {
        assertThatThrownBy(() -> packagingService.create(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Packaging must not be null");
    }

    @Test
    void create_whenValidPackaging_persistsAndReturnsPackaging() throws Exception {
        var created = packagingService.create(new Packaging(null, "Small Box", "Desc",
                10, 20, 30, 0, 1.5));

        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Small Box");
        assertThat(created.lengthCm()).isEqualTo(10);
        assertThat(created.heightCm()).isEqualTo(20);
        assertThat(created.widthCm()).isEqualTo(30);
        assertThat(created.internalVolumeCubicCm()).isEqualTo(10 * 20 * 30);
        assertThat(created.packagingCostAud()).isEqualTo(1.5);

        List<Packaging> all = packagingService.findAll();
        assertThat(all).hasSize(1);

        Path packagingsFile = tempDir
                .resolve(".postage-comparator")
                .resolve("packagings.json");
        assertThat(Files.exists(packagingsFile)).isTrue();
    }

    @Test
    void create_whenNameMissing_throwsBadRequestException() {
        var p = new Packaging(null, null, "Desc", 10, 20, 30, 0, 1.5);

        assertThatThrownBy(() -> packagingService.create(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Packaging name is required");
    }

    @Test
    void create_whenNameBlank_throwsBadRequestException() {
        var p = new Packaging(null, "   ", "Desc", 10, 20, 30, 0, 1.5);

        assertThatThrownBy(() -> packagingService.create(p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Packaging name is required");
    }

    @Test
    void create_whenDimensionsNonPositive_throwsBadRequestException() {
        var zeroLength = new Packaging(null, "Box", "Desc", 0, 20, 30, 0, 1.5);
        var negativeWidth = new Packaging(null, "Box", "Desc", 10, 20, -5, 0, 1.5);

        assertThatThrownBy(() -> packagingService.create(zeroLength))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dimensions (length, height, width) must be greater than 0");
        assertThatThrownBy(() -> packagingService.create(negativeWidth))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dimensions (length, height, width) must be greater than 0");
    }

    @Test
    void create_whenCostNonPositive_throwsBadRequestException() {
        var zeroCost = new Packaging(null, "Box", "Desc", 10, 20, 30, 0, 0.0);
        var negativeCost = new Packaging(null, "Box", "Desc", 10, 20, 30, 0, -1.0);

        assertThatThrownBy(() -> packagingService.create(zeroCost))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Packaging cost must be greater than 0");
        assertThatThrownBy(() -> packagingService.create(negativeCost))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Packaging cost must be greater than 0");
    }

    @Test
    void create_whenDuplicateName_throwsBadRequestException() {
        packagingService.create(new Packaging(null, "Box", "First",
                10, 20, 30, 0, 1.5));

        assertThatThrownBy(() ->
                packagingService.create(new Packaging(null, "Box", "Second",
                        15, 25, 35, 0, 2.0))
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_whenCustomIdProvided_isIgnored() {
        var created = packagingService.create(new Packaging("custom-id", "Box", "Desc",
                10, 20, 30, 0, 1.5));

        assertThat(created.id()).isNotNull();
        assertThat(created.id()).isNotEqualTo("custom-id");
    }

    @Test
    void create_whenInternalVolumeProvided_usesProvidedVolume() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 1234, 1.5));

        assertThat(created.internalVolumeCubicCm()).isEqualTo(1234);
    }

    @Test
    void create_thenFindById_returnsPackaging() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        var found = packagingService.findById(created.id());
        assertThat(found).isPresent();
        assertThat(found.orElseThrow().name()).isEqualTo("Box");
    }

    @Test
    void findById_whenSpuriousId_returnsEmptyOptional() {
        var result = packagingService.findById("non-existent-id");
        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenUsingNameInsteadOfId_returnsEmptyOptional() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        var result = packagingService.findById(created.name());
        assertThat(result).isEmpty();
    }

    @Test
    void findById_whenIdIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> packagingService.findById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id must not be null or blank");
    }

    @Test
    void findById_whenIdBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> packagingService.findById("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Id must not be null or blank");
    }

    @Test
    void update_whenIdMissing_throwsBadRequestException() {
        var p = new Packaging(null, "Box", "Desc", 10, 20, 30, 0, 1.5);

        assertThatThrownBy(() -> packagingService.update(null, p))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void update_whenBodyMissing_throwsBadRequestException() {
        assertThatThrownBy(() -> packagingService.update("some-id", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("packaging must not be null");
    }

    @Test
    void update_whenIdDoesNotExist_throwsNotFoundException() {
        var p = new Packaging(null, "Box", "Desc", 10, 20, 30, 0, 1.5);

        assertThatThrownBy(() -> packagingService.update("non-existent-id", p))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void update_whenRenamingToExistingName_throwsBadRequestException() {
        packagingService.create(new Packaging(null, "Box1", "Desc1",
                10, 20, 30, 0, 1.5));
        var p2 = packagingService.create(new Packaging(null, "Box2", "Desc2",
                15, 25, 35, 0, 2.0));

        var updateP2 = new Packaging(null, "Box1", "New desc",
                15, 25, 35, 0, 2.0);

        assertThatThrownBy(() -> packagingService.update(p2.id(), updateP2))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_withoutName_keepsExistingName() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        var update = new Packaging(null, null, "New desc",
                0, 0, 0, 0, 0.0);
        var updated = packagingService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("Box");
        assertThat(updated.description()).isEqualTo("New desc");
        assertThat(updated.lengthCm()).isEqualTo(10);
        assertThat(updated.heightCm()).isEqualTo(20);
        assertThat(updated.widthCm()).isEqualTo(30);
        assertThat(updated.internalVolumeCubicCm()).isEqualTo(created.internalVolumeCubicCm());
        assertThat(updated.packagingCostAud()).isEqualTo(1.5);
    }

    @Test
    void update_withoutDescription_keepsExistingDescription() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        var update = new Packaging(null, "New name", null,
                15, 25, 35, 0, 2.0);
        var updated = packagingService.update(created.id(), update);

        assertThat(updated.name()).isEqualTo("New name");
        assertThat(updated.description()).isEqualTo("Desc");
        assertThat(updated.lengthCm()).isEqualTo(15);
        assertThat(updated.heightCm()).isEqualTo(25);
        assertThat(updated.widthCm()).isEqualTo(35);
        assertThat(updated.packagingCostAud()).isEqualTo(2.0);
    }

    @Test
    void update_withoutInternalVolume_keepsExistingVolume() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 1234, 1.5));

        var update = new Packaging(null, "New name", "New desc",
                15, 25, 35, 0, 2.0);
        var updated = packagingService.update(created.id(), update);

        assertThat(updated.internalVolumeCubicCm()).isEqualTo(1234);
    }

    @Test
    void update_withoutCost_keepsExistingCost() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        var update = new Packaging(null, "New name", "New desc",
                15, 25, 35, 0, 0.0);
        var updated = packagingService.update(created.id(), update);

        assertThat(updated.packagingCostAud()).isEqualTo(1.5);
    }

    @Test
    void delete_whenIdMissing_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> packagingService.delete(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void delete_whenIdBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> packagingService.delete("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must not be null or blank");
    }

    @Test
    void delete_whenIdDoesNotExist_stillPersistsExistingPackagings() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));

        packagingService.delete("non-existent-id");

        var all = packagingService.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.getFirst().id()).isEqualTo(created.id());
    }

    @Test
    void delete_whenExistingPackaging_thenFindAllReturnsEmpty() {
        var created = packagingService.create(new Packaging(null, "Box", "Desc",
                10, 20, 30, 0, 1.5));
        assertThat(packagingService.findAll()).hasSize(1);

        packagingService.delete(created.id());

        var all = packagingService.findAll();
        assertThat(all).isEmpty();
    }

    @Test
    void findAll_whenPackagingsFileCorrupted_throwsIllegalStateException() throws Exception {
        Path packagingsFile = tempDir
                .resolve(".postage-comparator")
                .resolve("packagings.json");
        Files.createDirectories(packagingsFile.getParent());
        Files.writeString(packagingsFile, "not valid json");

        assertThatThrownBy(() -> packagingService.findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to read packagings");
    }
}

