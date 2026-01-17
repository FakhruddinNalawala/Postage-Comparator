package com.postage.postagecomparator.service;

import com.postage.postagecomparator.model.Packaging;

import java.util.List;
import java.util.Optional;

public interface PackagingService {

    List<Packaging> findAll();

    Optional<Packaging> findById(String id);

    Packaging create(Packaging packaging);

    Packaging update(String id, Packaging packaging);

    void delete(String id);
}
