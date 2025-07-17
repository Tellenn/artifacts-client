package com.tellenn.artifacts.db.documents

import com.tellenn.artifacts.clients.models.Resource
import com.tellenn.artifacts.clients.models.ResourceDrop
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Document representation of a resource for MongoDB storage.
 */
@Document(collection = "resources")
data class ResourceDocument(
    @Id
    val code: String,
    val name: String,
    val skill: String,
    val level: Int,
    val drops: List<ResourceDropDocument>?
) {
    companion object {
        /**
         * Converts a Resource model to a ResourceDocument.
         */
        fun fromResource(resource: Resource): ResourceDocument {
            return ResourceDocument(
                code = resource.code,
                name = resource.name,
                skill = resource.skill,
                level = resource.level,
                drops = resource.drops.map { ResourceDropDocument.fromResourceDrop(it) }
            )
        }

        /**
         * Converts a ResourceDocument to a Resource model.
         */
        fun toResource(resourceDocument: ResourceDocument): Resource {
            return Resource(
                code = resourceDocument.code,
                name = resourceDocument.name,
                skill = resourceDocument.skill,
                level = resourceDocument.level,
                drops = resourceDocument.drops?.map { ResourceDropDocument.toResourceDrop(it) } ?: emptyList()
            )
        }
    }
}

/**
 * Document representation of a resource drop for MongoDB storage.
 */
data class ResourceDropDocument(
    val code: String,
    val rate: Int,
    val minQuantity: Int,
    val maxQuantity: Int
) {
    companion object {
        /**
         * Converts a ResourceDrop model to a ResourceDropDocument.
         */
        fun fromResourceDrop(resourceDrop: ResourceDrop): ResourceDropDocument {
            return ResourceDropDocument(
                code = resourceDrop.code,
                rate = resourceDrop.rate,
                minQuantity = resourceDrop.minQuantity,
                maxQuantity = resourceDrop.maxQuantity
            )
        }

        /**
         * Converts a ResourceDropDocument to a ResourceDrop model.
         */
        fun toResourceDrop(resourceDropDocument: ResourceDropDocument): ResourceDrop {
            return ResourceDrop(
                code = resourceDropDocument.code,
                rate = resourceDropDocument.rate,
                minQuantity = resourceDropDocument.minQuantity,
                maxQuantity = resourceDropDocument.maxQuantity
            )
        }
    }
}