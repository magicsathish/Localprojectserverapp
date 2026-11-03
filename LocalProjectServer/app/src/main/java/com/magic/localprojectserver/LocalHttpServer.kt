package com.magic.localprojectserver

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class LocalHttpServer(
    port: Int,
    private val webRootDir: File
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            val res = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            addCorsHeaders(res)
            return res
        }

        var uri = session.uri
        if (uri.endsWith("/")) {
            uri += "index.html"
        }

        val cleanPath = uri.removePrefix("/").replace("/", File.separator)
        val targetFile = File(webRootDir, cleanPath)

        val rootCanonical = try { webRootDir.canonicalPath } catch (e: Exception) { "" }
        val targetCanonical = try { targetFile.canonicalPath } catch (e: Exception) { "" }

        if (!targetCanonical.startsWith(rootCanonical)) {
            val res = newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "403 Access Denied")
            addCorsHeaders(res)
            return res
        }

        return if (targetFile.exists() && targetFile.isFile) {
            try {
                val mimeType = resolveMimeType(targetFile.name)
                val stream = FileInputStream(targetFile)
                val response = newChunkedResponse(Response.Status.OK, mimeType, stream)
                addCorsHeaders(response)
                response
            } catch (e: IOException) {
                val res = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Error: ${e.message}")
                addCorsHeaders(res)
                res
            }
        } else {
            val res = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found: $uri")
            addCorsHeaders(res)
            res
        }
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "*")
        response.addHeader("Service-Worker-Allowed", "/")
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
    }

    private fun resolveMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "js", "mjs" -> "application/javascript"
            "json", "map" -> "application/json"
            "css" -> "text/css"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "gltf" -> "model/gltf+json"
            "glb" -> "model/gltf-binary"
            "bin" -> "application/octet-stream"
            "wasm" -> "application/wasm"
            "ttf" -> "font/ttf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "application/octet-stream"
        }
    }
}
