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
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.regex.Pattern;

@ExtendWith(DropwizardExtensionsSupport.class)
public abstract class LayerDatabaseFixture extends AbstractTestWithTestDir {
    protected final DAOTestExtension daoTestExtension = DAOTestExtension.newBuilder()
        .addEntityClass(ListingRecord.class)
        .build();
    protected LayerDatabaseImpl dao;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        dao = new LayerDatabaseImpl(daoTestExtension.getSessionFactory());
    }

    protected void saveDbRow(Long layerId, String path, Listing.Type type) {
        var details = ListingRecord.builder()
            .layerId(layerId)
            .path(path)
            .type(type);
        dao.save(details.build());
    }

    protected LayeredStorage createLayeredStorage(LayerManager layerManager) {
        return new LayeredStorage(
            layerManager,
            dao,
            new InventoryFilter(stagingDir)
        );
    }
}
