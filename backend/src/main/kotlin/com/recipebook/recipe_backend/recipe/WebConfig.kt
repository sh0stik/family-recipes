package com.recipebook.recipe_backend.recipe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig(
    // The same folder as photo/UploadsFolder, where uploads are written and removed.
    @Value("\${recipe.uploads-dir:uploads}") private val uploadsDir: String,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Trailing slash required: a resource location without it is treated as a file, not a folder.
        val uploadPath = Paths.get(uploadsDir).toAbsolutePath().toUri().toString().let {
            if (it.endsWith("/")) it else "$it/"
        }
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(uploadPath)
    }
}
