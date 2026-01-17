package com.postage.postagecomparator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postage.postagecomparator.exception.BadRequestException;
import com.postage.postagecomparator.exception.NotFoundException;
import com.postage.postagecomparator.model.Packaging;
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
public class PackagingServiceImpl implements PackagingService {

    private static final Logger log = LoggerFactory.getLogger(PackagingServiceImpl.class);

    private static final String DEFAULT_DIR_NAME = ".postage-comparator";
    private static final String PACKAGINGS_FILE_NAME = "packagings.json";

    private final ObjectMapper objectMapper;
    private final Object lock = new Object();

    public PackagingServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Packaging> findAll() {
        return listAllPackagings();
    }

    @Override
    public Optional<Packaging> findById(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id must not be null or blank");
        }
        return listAllPackagings().stream()
                .filter(packaging -> packaging.id().equals(id))
                .findFirst();
    }

    @Override
    public Packaging create(Packaging packaging) {
        if (packaging == null) {
            throw new BadRequestException("Packaging must not be null");
        }
        if (packaging.name() == null || packaging.name().isBlank()) {
            throw new BadRequestException("Packaging name is required");
        }
        if (packaging.lengthCm() <= 0 || packaging.heightCm() <= 0 || packaging.widthCm() <= 0) {
            throw new BadRequestException("Packaging dimensions (length, height, width) must be greater than 0");
        }
        if (packaging.packagingCostAud() <= 0) {
            throw new BadRequestException("Packaging cost must be greater than 0");
        }
        
        var volume = packaging.internalVolumeCubicCm() > 0 
                ? packaging.internalVolumeCubicCm() 
                : packaging.lengthCm() * packaging.heightCm() * packaging.widthCm();

        var packagings = listAllPackagings();
        if (packagings.stream().anyMatch(p -> p.name().equals(packaging.name()))) {
            throw new BadRequestException("Packaging with name " + packaging.name() + " already exists");
        }

        var newPackaging = new Packaging(
                generatePackagingId(),
                packaging.name(),
                packaging.description(),
                packaging.lengthCm(),
                packaging.heightCm(),
                packaging.widthCm(),
                volume,
                packaging.packagingCostAud());
        packagings.add(newPackaging);
        savePackagings(packagings);
        return newPackaging;
    }

    @Override
    public Packaging update(String id, Packaging packaging) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("id must not be null or blank");
        }
        if (packaging == null) {
            throw new BadRequestException("packaging must not be null");
        }

        var packagings = listAllPackagings();
        if (packaging.name() != null && !packaging.name().isBlank()) {
            boolean duplicateExists = packagings.stream()
                    .anyMatch(p -> p.name().equals(packaging.name()) && !p.id().equals(id));
            if (duplicateExists) {
                throw new BadRequestException("Packaging with name " + packaging.name() + " already exists");
            }
        }

        var existingIndex = -1;
        Packaging existing = null;
        for (int i = 0; i < packagings.size(); i++) {
            if (id.equals(packagings.get(i).id())) {
                existingIndex = i;
                existing = packagings.get(i);
                break;
            }
        }

        if (existing == null) {
            throw new NotFoundException("Packaging with id " + id + " not found");
        }

        var updated = new Packaging(
                existing.id(),
                packaging.name() != null && !packaging.name().isBlank() ? packaging.name() : existing.name(),
                packaging.description() != null ? packaging.description() : existing.description(),
                packaging.lengthCm() > 0 ? packaging.lengthCm() : existing.lengthCm(),
                packaging.heightCm() > 0 ? packaging.heightCm() : existing.heightCm(),
                packaging.widthCm() > 0 ? packaging.widthCm() : existing.widthCm(),
                packaging.internalVolumeCubicCm() > 0 ? packaging.internalVolumeCubicCm()
                        : existing.internalVolumeCubicCm(),
                packaging.packagingCostAud() > 0 ? packaging.packagingCostAud() : existing.packagingCostAud());
        packagings.set(existingIndex, updated);
        savePackagings(packagings);
        return updated;
    }

    @Override
    public void delete(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be null or blank");
        }
        var packagings = listAllPackagings().stream()
                .filter(packaging -> !packaging.id().equals(id))
                .toList();
        savePackagings(packagings);
    }

    private Path packagingsPath() {
        var baseDir = System.getProperty("POSTAGE_DATA_DIR");
        if (baseDir == null || baseDir.isBlank()) {
            baseDir = System.getenv("POSTAGE_DATA_DIR");
        }
        if (baseDir == null || baseDir.isBlank()) {
            var userHome = System.getProperty("user.home");
            baseDir = Path.of(userHome, DEFAULT_DIR_NAME).toString();
        }
        return Path.of(baseDir, PACKAGINGS_FILE_NAME);
    }

    private List<Packaging> listAllPackagings() {
        var path = packagingsPath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        synchronized (lock) {
            try {
                var packagings = objectMapper.readValue(path.toFile(), new TypeReference<List<Packaging>>() {
                });
                return packagings != null ? new ArrayList<>(packagings) : new ArrayList<>();
            } catch (IOException e) {
                log.error("Failed to read packagings from {}", path, e);
                throw new IllegalStateException("Unable to read packagings", e);
            }
        }
    }

    private String generatePackagingId() {
        return UUID.randomUUID().toString();
    }

    private void savePackagings(List<Packaging> packagings) {
        synchronized (lock) {
            Path path = packagingsPath();
            FileWriteUtils.safeWrite(path, temp -> {
                try {
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValue(temp.toFile(), packagings);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to write packagings to " + path, e);
                }
            }, log);
        }
    }
}
