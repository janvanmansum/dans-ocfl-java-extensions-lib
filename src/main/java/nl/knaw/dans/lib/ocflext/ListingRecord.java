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

import io.ocfl.core.storage.common.Listing;
import lombok.Builder;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.NamedQuery;

@Data
@Builder(builderClassName = "Builder")
@Entity(name = "listing_record")
@NamedQuery(
    name = "ListingRecord.getByPath",
    query = "SELECT l FROM listing_record l WHERE l.path = :path"
)
@NamedQuery(name = "ListingRecord.listAll",
            query = "SELECT l FROM listing_record l")
@NamedQuery(
    name = "ListingRecord.listDirectory",
    query = """
        SELECT l
        FROM listing_record l
        WHERE l.path LIKE :path
            AND l.path NOT LIKE :pathWithTwoComponents
            AND l.layerId IN (SELECT MAX(l2.layerId)
                              FROM listing_record l2
                              WHERE l2.path LIKE :path AND l2.path NOT LIKE :pathWithTwoComponents GROUP BY l2.path)"""
)
@NamedQuery(
    name = "ListingRecord.listRecursive",
    query = """
        SELECT l
        FROM listing_record l
        WHERE l.path LIKE :path
            AND l.layerId IN (SELECT MAX(l2.layerId)
                              FROM listing_record l2
                              WHERE l2.path LIKE :path GROUP BY l2.path)"""
)
public class ListingRecord {

    @Id
    @Column(name = "generated_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long generatedId;

    @Column(name = "layer_id", nullable = false)
    private Long layerId;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private Listing.Type type;

    @Column
    @Lob
    private byte[] content;

    public Listing toListing() {
        return new Listing(type, path);
    }

}
