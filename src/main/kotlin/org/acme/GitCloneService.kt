package org.acme

import io.quarkus.runtime.Startup
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files


@ApplicationScoped
class GitCloneService {

    @ConfigProperty(name = "repo.url")
    var repoUrl: String? = null

    @ConfigProperty(name = "branch.name")
    var branchName: String? = null

    @ConfigProperty(name = "local.path")
    var localPath: String? = null

    // In-memory storage for .kts files
    private val ktsFilesInMemory: MutableMap<String, String> = mutableMapOf()

    @Startup
    fun onStartup() {
        val localRepoPath = localPath?.let { File(it) }
        val result = StringBuilder()

        try {
            // If the directory exists, delete its contents
            if (localRepoPath != null) {
                if (localRepoPath.exists()) {
                    deleteDirectoryRecursively(localRepoPath)
                }
            }

            val git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(localRepoPath)
                .setBranch(branchName)
                .call()

            for (remoteName in git.repository.remoteNames) {
                result.append(remoteName).append("\n")
            }

            // Checkout the branch
            val ref = git.checkout().setName(branchName).call()

            // List the files in the branch
            listFilesInBranch(git, result)

            println(result.toString())
        } catch (e: GitAPIException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Helper method to list files in the current branch
    @Throws(IOException::class, GitAPIException::class)
    fun listFilesInBranch(git: Git, result: StringBuilder) {
        TreeWalk(git.repository).use { treeWalk ->
            treeWalk.addTree(
                RevWalk(git.repository).parseTree(git.repository.resolve("HEAD"))
            )
            treeWalk.isRecursive = true
            while (treeWalk.next()) {
                if(treeWalk.pathString.endsWith(".kts")) {
                    val objectId = treeWalk.getObjectId(0)
                    storeFileInMemory(git, objectId, treeWalk.pathString)
                    result.append(treeWalk.pathString).append("\n")
                }
            }
        }
    }

    // Helper method to store .kts files in memory
    @Throws(IOException::class, GitAPIException::class)
    fun storeFileInMemory(git: Git, objectId: AnyObjectId, filePath: String) {
        val repository = git.repository
        val reader = repository.newObjectReader()
        val objectLoader = reader.open(objectId)
        val output = ByteArrayOutputStream()
        objectLoader.copyTo(output)
        ktsFilesInMemory[filePath] = output.toString("UTF-8")
        println(ktsFilesInMemory[filePath])
    }



    companion object {
        // Helper method to delete directory recursively
        @Throws(IOException::class)
        fun deleteDirectoryRecursively(directory: File) = Files.walk(directory.toPath())
            .map { it.toFile() }
            .sorted { o1: File, o2: File? -> -o1.compareTo(o2) }
            .forEach { obj: File -> obj.delete() }
    }
}
