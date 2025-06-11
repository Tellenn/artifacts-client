package com.tellenn.artifacts.utils

import com.tellenn.artifacts.TellennArtifactsClientApplication
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

/**
 * Utility class to run the item sync process directly.
 */
object ItemSyncUtil {
    
    /**
     * Main method to run the item sync process.
     * This will start the Spring application with the item-sync profile active,
     * which will trigger the ItemSyncRunner to run the syncAllItems function.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val application = SpringApplication(TellennArtifactsClientApplication::class.java)
        application.setAdditionalProfiles("item-sync")
        val context: ConfigurableApplicationContext = application.run(*args)
        
        // The application will automatically exit after the ItemSyncRunner completes
        // If you want to keep it running, you can add a loop or wait here
    }
}