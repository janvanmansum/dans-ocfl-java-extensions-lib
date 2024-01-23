/*
 * Copyright (C) 2023 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.lib.ocflext;

import io.ocfl.api.OcflFileRetriever;
import io.ocfl.api.exception.OcflIOException;
import io.ocfl.api.model.DigestAlgorithm;
import io.ocfl.core.storage.common.Listing;
import io.ocfl.core.storage.common.OcflObjectRootDirIterator;
import io.ocfl.core.storage.common.Storage;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class LayeredStorage implements Storage {
    // Note: the @param tags are necessary to make sure the code generated by @Builder has complete JavaDocs after delomboking.
    /**
     * @param layerManager the layer manager
     */
    private LayerManager layerManager;

    /**
     * @param layerDatabase the layer database
     */
    private LayerDatabase layerDatabase;

    /**
     * @param databaseBackedFilesFilter the database backed files filter
     */
    @Builder.Default
    private Filter<Path> databaseBackedFilesFilter = path -> false;

    @Override
    public List<Listing> listDirectory(String directoryPath) {
        try {
            return layerDatabase
                .listDirectory(directoryPath)
                .stream()
                .map(r -> r.toListing(directoryPath))
                .collect(Collectors.toList());
        }
        catch (IOException e) {
            throw OcflIOException.from(e);
        }
    }

    @Override
    public List<Listing> listRecursive(String directoryPath) {
        try {
            return layerDatabase.listRecursive(directoryPath)
                .stream()
                .map(r -> r.toListing(directoryPath))
                .collect(Collectors.toList());
        }
        catch (IOException e) {
            throw OcflIOException.from(e);
        }
    }

    @Override
    public boolean directoryIsEmpty(String directoryPath) {
        return listDirectory(directoryPath).isEmpty();
    }

    @Override
    public OcflObjectRootDirIterator iterateObjects() {
        return null;
    }

    @Override
    public boolean fileExists(String filePath) {
        return !layerDatabase.findLayersContaining(filePath).isEmpty();
    }

    @SneakyThrows
    @Override
    public InputStream read(String filePath) {
        if (layerDatabase.isContentStoredInDatabase(filePath)) {
            return new ByteArrayInputStream(layerDatabase.readContentFromDatabase(filePath));
        }
        else {
            return layerDatabase.findLayersContaining(filePath).stream()
                .sorted(Collections.reverseOrder()) // Get the newest layer first
                .map(layerManager::getLayer)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No layer found for file: " + filePath))
                .read(filePath);
        }
    }

    @SneakyThrows
    @Override
    public String readToString(String filePath) {
        StringWriter writer = new StringWriter();
        try (InputStream is = read(filePath)) {
            IOUtils.copy(is, writer, StandardCharsets.UTF_8);
        }
        return writer.toString();
    }

    @Override
    public OcflFileRetriever readLazy(String filePath, DigestAlgorithm algorithm, String digest) {
        return null;
    }

    @SneakyThrows
    @Override
    public void write(String filePath, byte[] content, String mediaType) {
        layerManager.getTopLayer().write(filePath, IOUtils.toInputStream(new String(content), StandardCharsets.UTF_8));
        layerDatabase.addFile(layerManager.getTopLayer().getId(), filePath);
    }

    @SneakyThrows
    @Override
    public void createDirectories(String path) {
        layerManager.getTopLayer().createDirectories(path);
        layerDatabase.addDirectories(layerManager.getTopLayer().getId(), path);
    }

    @Override
    @SneakyThrows
    public void copyDirectoryOutOf(String source, Path destination) {
        for (ListingRecord listingRecord : layerDatabase.listRecursive(source)) { // TODO: sorted ascending by length, so that we are guaranteed to create parent directories before contained files?
            if (listingRecord.getType().equals(Listing.Type.Directory)) {
                Files.createDirectories(destination.resolve(listingRecord.getPath()));
            }
            try (OutputStream os = Files.newOutputStream(destination.resolve(listingRecord.getPath()))) {
                IOUtils.copy(read(listingRecord.getPath()), os);
            }
        }
    }

    @SneakyThrows
    @Override
    public void copyFileInto(Path source, String destination, String mediaType) {
        layerManager.getTopLayer().write(destination, Files.newInputStream(source));
        layerDatabase.addFile(layerManager.getTopLayer().getId(), destination);
    }

    @SneakyThrows
    @Override
    public void copyFileInternal(String sourceFile, String destinationFile) {
        layerManager.getTopLayer().write(destinationFile, read(sourceFile));
        layerDatabase.addFile(layerManager.getTopLayer().getId(), destinationFile);
    }

    @Override
    @SneakyThrows
    public void moveDirectoryInto(Path source, String destination) {
        var parent = Path.of(destination).getParent();
        var newListingRecordsUpToDestination = layerDatabase.addDirectories(
            layerManager.getTopLayer().getId(),
            parent.toString());
        if (!newListingRecordsUpToDestination.isEmpty()) {
            layerManager.getTopLayer().createDirectories(parent.toString());
        }
        // Create listing records for all files in the moved directory
        var records = new ArrayList<ListingRecord>();
        try (var s = Files.walk(source)) {
            s.forEach(path -> {
                var destPath = destination + "/" + source.relativize(path);
                var r = new ListingRecord.Builder()
                    .layerId(layerManager.getTopLayer().getId())
                    .path(destPath)
                    .type(getListingType(path)).build();
                if (databaseBackedFilesFilter.accept(path)) {
                    byte[] content = readToString(destPath).getBytes(StandardCharsets.UTF_8);
                    log.debug("Adding content of file {} to database; file length = {}", destPath, content.length);
                    r.setContent(content);
                }
                records.add(r);
            });
        }
        layerManager.getTopLayer().moveDirectoryInto(source, destination);
        layerDatabase.saveRecords(records);
        layerDatabase.saveRecords(newListingRecordsUpToDestination);
        // TODO: rollback move on disk if database update fails
    }

    private Listing.Type getListingType(Path path) {
        Listing.Type type;
        if (Files.isDirectory(path)) {
            type = Listing.Type.Directory;
        }
        else if (Files.isRegularFile(path)) {
            type = Listing.Type.File;
        }
        else {
            type = Listing.Type.Other;
        }
        return type;
    }

    @Override
    @SneakyThrows
    public void moveDirectoryInternal(String source, String destination) {
        checkAllSourceFilesInTopLayer(source, "moveDirectoryInternal");
        layerManager.getTopLayer().moveDirectoryInternal(source, destination);
        // Update listing records for all files in the moved directory
        var records = layerDatabase.listRecursive(source);
        for (ListingRecord record : records) {
            var destPath = destination + "/" + source.substring(source.lastIndexOf("/") + 1) + record.getPath().substring(source.length());
            record.setPath(destPath);
        }
        layerDatabase.saveRecords(records);
    }

    @SneakyThrows
    private void checkAllSourceFilesInTopLayer(String source, String methodName) {
        for (ListingRecord listingRecord : layerDatabase.listRecursive(source)) {
            if (!layerManager.getTopLayer().fileExists(listingRecord.getPath())) {
                throw new IllegalStateException("File " + listingRecord.getPath() + " is not in the top layer. " + methodName
                    + " can only be called if source is completely in the top layer.");
            }
        }
    }

    @Override
    @SneakyThrows
    public void deleteDirectory(String path) {
        checkAllSourceFilesInTopLayer(path, "deleteDirectory");
        layerManager.getTopLayer().deleteDirectory(path);
        layerDatabase.deleteRecords(layerDatabase.listRecursive(path));
    }

    @Override
    public void deleteFile(String path) {
        deleteFiles(Collections.singletonList(path));
    }

    @Override
    @SneakyThrows
    public void deleteFiles(Collection<String> paths) {
        // Build a map from layer to paths in that layer
        var layerPaths = new HashMap<Long, List<String>>();
        for (String path : paths) {
            var layers = layerDatabase.findLayersContaining(path);
            for (Long layerId : layers) {
                layerPaths.computeIfAbsent(layerId, k -> new ArrayList<>()).add(path);
            }
        }
        // Delete the files in each layer
        for (var entry : layerPaths.entrySet()) {
            layerManager.getLayer(entry.getKey())
                .deleteFiles(entry.getValue());
        }
    }

    @Override
    public void deleteEmptyDirsDown(String path) {
        List<ListingRecord> containedListings;
        try {
            containedListings = layerDatabase.listRecursive(path);
        }
        catch (IOException e) {
            throw OcflIOException.from(e);
        }
        // Sort by descending path length, so that we start with the deepest directories
        containedListings.sort((listingRecord1, listingRecord2) ->
            Integer.compare(listingRecord2.getPath().length(), listingRecord1.getPath().length()));
        for (ListingRecord listingRecord : containedListings) {
            if (listingRecord.getType().equals(Listing.Type.Directory)) {
                if (directoryIsEmpty(listingRecord.getPath())) {
                    // Recursive delete not supported in archived layers
                    if (!listingRecord.getLayerId().equals(layerManager.getTopLayer().getId())) {
                        throw new IllegalStateException("Trying to delete empty directory from non-top layer: " + listingRecord.getPath() + ". This is not allowed.");
                    }
                    deleteDirectory(listingRecord.getPath());
                }
            }
        }
    }

    @Override
    public void deleteEmptyDirsUp(String path) {
        var pathParts = path.split("/");
        for (int i = pathParts.length - 1; i >= 0; i--) {
            var parentPath = String.join("/", pathParts).substring(0, String.join("/", pathParts).lastIndexOf("/"));
            if (directoryIsEmpty(parentPath)) {
                deleteDirectory(parentPath);
            }
        }
    }
}
