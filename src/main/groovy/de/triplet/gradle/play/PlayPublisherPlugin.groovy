package de.triplet.gradle.play

import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class PlayPublisherPlugin implements Plugin<Project> {

    public static final PLAY_STORE_GROUP = 'Play Store'

    @Override
    void apply(Project project) {
        def log = project.logger

        if (!project.plugins.any { p -> p instanceof AppPlugin }) {
            throw new IllegalStateException('The \'com.android.application\' plugin is required.')
        }

        def extension = project.extensions.create('play', PlayPublisherPluginExtension)

        project.android.extensions.playAccountConfigs = project.container(PlayAccountConfig)

        project.android.defaultConfig.ext.playAccountConfig = null

        project.android.productFlavors.whenObjectAdded { flavor ->
            flavor.ext.playAccountConfig = flavor.project.android.defaultConfig.ext.playAccountConfig
        }

        project.android.applicationVariants.whenObjectAdded { variant ->
            if (variant.buildType.isDebuggable()) {
                log.debug("Skipping debuggable build type ${variant.buildType.name}.")
                return
            }

            def bootstrapTaskName = "bootstrap${variant.name.capitalize()}PlayResources"
            def playResourcesTaskName = "generate${variant.name.capitalize()}PlayResources"
            def publishApkTaskName = "publishApk${variant.name.capitalize()}"
            def publishListingTaskName = "publishListing${variant.name.capitalize()}"
            def publishTaskName = "publish${variant.name.capitalize()}"
            def promoteAlphaToBetaTaskName = "promote${variant.name.capitalize()}AlphaToBeta"
            def promoteAlphaToProductionTaskName = "promote${variant.name.capitalize()}AlphaToProduction"
            def promoteBetaToProductionTaskName = "promote${variant.name.capitalize()}BetaToProduction"

            // Create and configure bootstrap task for this variant.
            def bootstrapTask = project.tasks.create(bootstrapTaskName, BootstrapTask)
            bootstrapTask.extension = extension
            bootstrapTask.variant = variant
            def accountConfigForFlavor = variant.productFlavors.find {it.playAccountConfig}?.playAccountConfig
            def defaultAccountConfig = project.android.defaultConfig.ext.playAccountConfig
            if (!variant.flavorName.isEmpty()) {
                bootstrapTask.outputFolder = new File(project.projectDir, "src/${variant.flavorName}/play")
            } else {
                bootstrapTask.outputFolder = new File(project.projectDir, 'src/main/play')
            }
            bootstrapTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
            bootstrapTask.description = "Downloads the play store listing for the ${variant.name.capitalize()} build. No download of image resources. See #18."
            bootstrapTask.group = PLAY_STORE_GROUP

            // Create and configure task to collect the play store resources.
            def playResourcesTask = project.tasks.create(playResourcesTaskName, GeneratePlayResourcesTask)

            playResourcesTask.inputs.file(new File(project.projectDir, 'src/main/play'))
            if (!variant.flavorName.isEmpty()) {
                playResourcesTask.inputs.file(new File(project.projectDir, "src/${variant.flavorName}/play"))
            }
            playResourcesTask.inputs.file(new File(project.projectDir, "src/${variant.buildType.name}/play"))
            playResourcesTask.inputs.file(new File(project.projectDir, "src/${variant.name}/play"))

            playResourcesTask.outputFolder = new File(project.projectDir, "build/outputs/play/${variant.name}")
            playResourcesTask.description = "Collects play store resources for the ${variant.name.capitalize()} build"
            playResourcesTask.group = PLAY_STORE_GROUP

            // Create and configure publisher meta task for this variant
            def publishListingTask = project.tasks.create(publishListingTaskName, PlayPublishListingTask)
            publishListingTask.extension = extension
            publishListingTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
            publishListingTask.variant = variant
            publishListingTask.inputFolder = playResourcesTask.outputFolder
            publishListingTask.description = "Updates the play store listing for the ${variant.name.capitalize()} build"
            publishListingTask.group = PLAY_STORE_GROUP

            // Attach tasks to task graph.
            publishListingTask.dependsOn playResourcesTask

            // Create promote tasks. This tasks do not related to variants.
            def promoteAlphaToBetaTask = project.tasks.create(promoteAlphaToBetaTaskName, PromoteAlphaToBetaTask)
            promoteAlphaToBetaTask.description = "Promote Alpha track apk to Beta track for the ${variant.name.capitalize()} build"
            promoteAlphaToBetaTask.extension = extension
            promoteAlphaToBetaTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
            promoteAlphaToBetaTask.variant = variant
            promoteAlphaToBetaTask.group = PLAY_STORE_GROUP

            def promoteAlphaToProductionTask = project.tasks.create(promoteAlphaToProductionTaskName, PromoteAlphaToProductionTask)
            promoteAlphaToProductionTask.description = "Promote Alpha track apk to Production track for the ${variant.name.capitalize()} build"
            promoteAlphaToProductionTask.extension = extension
            promoteAlphaToProductionTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
            promoteAlphaToProductionTask.variant = variant
            promoteAlphaToProductionTask.group = PLAY_STORE_GROUP

            def promoteBetaToProductionTask = project.tasks.create(promoteBetaToProductionTaskName, PromoteBetaToProductionTask)
            promoteBetaToProductionTask.description = "Promote Beta track apk to Production track for the ${variant.name.capitalize()} build"
            promoteBetaToProductionTask.extension = extension
            promoteBetaToProductionTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
            promoteBetaToProductionTask.variant = variant
            promoteBetaToProductionTask.group = PLAY_STORE_GROUP

            if (variant.isSigningReady()) {
                // Create and configure publisher apk task for this variant.
                def publishApkTask = project.tasks.create(publishApkTaskName, PlayPublishApkTask)
                publishApkTask.extension = extension
                publishApkTask.playAccountConfig = accountConfigForFlavor ?: defaultAccountConfig
                publishApkTask.variant = variant
                publishApkTask.inputFolder = playResourcesTask.outputFolder
                publishApkTask.description = "Uploads the APK for the ${variant.name.capitalize()} build"
                publishApkTask.group = PLAY_STORE_GROUP

                def publishTask = project.tasks.create(publishTaskName)
                publishTask.description = "Updates APK and play store listing for the ${variant.name.capitalize()} build"
                publishTask.group = PLAY_STORE_GROUP

                // Attach tasks to task graph.
                publishTask.dependsOn publishApkTask
                publishTask.dependsOn publishListingTask
                publishApkTask.dependsOn playResourcesTask

                publishApkTask.dependsOn(variant.assemble)
            } else {
                log.warn("Signing not ready. Did you specify a signingConfig for the variation ${variant.name.capitalize()}?")
            }
        }
    }

}
