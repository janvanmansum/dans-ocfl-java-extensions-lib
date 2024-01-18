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

import io.dropwizard.testing.junit5.DAOTestExtension;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.ocfl.core.storage.common.Listing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(DropwizardExtensionsSupport.class)
public class LayerDatabaseImplAddDirectoriesTest {

    private final DAOTestExtension daoTestExtension = DAOTestExtension.newBuilder()
        .addEntityClass(ListingRecord.class)
        .build();

    private LayerDatabaseImpl dao;

    @BeforeEach
    public void setUp() throws Exception {
        dao = new LayerDatabaseImpl(daoTestExtension.getSessionFactory());
    }

    @Test
    public void addDirectories_should_add_directories() {
        daoTestExtension.inTransaction(() -> dao.addDirectories(1L, "root/child/grandchild"));
        // Check that the directories were added, ignoring the generatedId
        assertThat(dao.listAll())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("generatedId")
            .containsExactlyInAnyOrder(
            ListingRecord.builder()
                .layerId(1L)
                .path("root")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child/grandchild")
                .type(Listing.Type.Directory)
                .build()
        );
    }

    @Test
    public void addDirectories_should_not_add_directories_if_they_already_exist_in_the_same_layer() {
        daoTestExtension.inTransaction(() -> dao.addDirectories(1L, "root/child/grandchild"));
        daoTestExtension.inTransaction(() -> dao.addDirectories(1L, "root/child/grandchild"));
        // Check that the directories were added, ignoring the generatedId
        assertThat(dao.listAll())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("generatedId")
            .containsExactlyInAnyOrder(
            ListingRecord.builder()
                .layerId(1L)
                .path("root")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child/grandchild")
                .type(Listing.Type.Directory)
                .build()
        );
    }

    @Test
    public void addDirectories_should_add_directories_even_if_they_already_exist_in_another_layer() {
        daoTestExtension.inTransaction(() -> dao.addDirectories(1L, "root/child/grandchild"));
        daoTestExtension.inTransaction(() -> dao.addDirectories(2L, "root/child/grandchild"));
        // Check that the directories were added, ignoring the generatedId
        assertThat(dao.listAll())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("generatedId")
            .containsExactlyInAnyOrder(
            ListingRecord.builder()
                .layerId(1L)
                .path("root")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child/grandchild")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(2L)
                .path("root")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(2L)
                .path("root/child")
                .type(Listing.Type.Directory)
                .build(),
            ListingRecord.builder()
                .layerId(2L)
                .path("root/child/grandchild")
                .type(Listing.Type.Directory)
                .build()
        );
    }

    @Test
    public void addDirectories_should_throw_an_IllegalArgumentException_if_the_path_contains_a_file_in_previous_layer() {
        daoTestExtension.inTransaction(() -> dao.addRecords(List.of(
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child/grandchild")
                .type(Listing.Type.File)
                .build()
        )));
        var e = assertThrows(IllegalArgumentException.class, () ->
            daoTestExtension.inTransaction(() -> dao.addDirectories(2L, "root/child/grandchild"))
        );
        assertThat(e.getMessage()).isEqualTo("Cannot add directory root/child/grandchild because it is already occupied by a file.");
    }

    @Test
    public void addDirectories_should_throw_an_IllegalArgumentException_if_the_path_contains_a_file_in_the_same_layer() {
        daoTestExtension.inTransaction(() -> dao.addRecords(List.of(
            ListingRecord.builder()
                .layerId(1L)
                .path("root/child/grandchild")
                .type(Listing.Type.File)
                .build()
        )));
        var e = assertThrows(IllegalArgumentException.class, () ->
            daoTestExtension.inTransaction(() -> dao.addDirectories(1L, "root/child/grandchild"))
        );
        assertThat(e.getMessage()).isEqualTo("Cannot add directory root/child/grandchild because it is already occupied by a file.");
    }
}
