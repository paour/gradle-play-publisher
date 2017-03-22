package de.triplet.gradle.play

import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.FileContent
import org.gradle.api.Project

class TaskHelper {

    def static readAndTrimFile(Project project, File file, int maxCharLength, boolean errorOnSizeLimit) {
        if (file.exists()) {
            def message = normalize(file.text)

            if (message.length() > maxCharLength) {
                if (errorOnSizeLimit) {
                    def resourcesOutput = project.file(PlayPublisherPlugin.RESOURCES_OUTPUT_PATH)
                    def relativePath = resourcesOutput.toURI().relativize(file.toURI())
                    throw new IllegalArgumentException("File '${relativePath}' has reached the limit of ${maxCharLength} characters")
                }

                return message.substring(0, maxCharLength)
            }

            return message
        }

        return ""
    }

    def static normalize(String text) {
        return text.replaceAll("\\r\\n", "\n")
    }

    def static List<AbstractInputStreamContent> getImageListAsStream(File listingDir, String graphicPath) {
        File graphicDir = new File(listingDir, graphicPath)
        if (graphicDir.exists()) {
            return graphicDir.listFiles(new ImageFileFilter()).sort().collect { file ->
                new FileContent(AndroidPublisherHelper.MIME_TYPE_IMAGE, file);
            }
        }
        return null
    }

    def static AbstractInputStreamContent getImageAsStream(File listingDir, String graphicPath) {
        File graphicDir = new File(listingDir, graphicPath)
        if (graphicDir.exists()) {
            File[] files = graphicDir.listFiles(new ImageFileFilter())
            if (files.length > 0) {
                File graphicFile = files[0]
                return new FileContent(AndroidPublisherHelper.MIME_TYPE_IMAGE, graphicFile);
            }
        }
        return null
    }

    def static readSingleLine(File file) {
        if (file.exists()) {
            file.withReader { return it.readLine() }
        }
    }
}
