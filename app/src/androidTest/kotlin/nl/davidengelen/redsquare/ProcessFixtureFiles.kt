package nl.davidengelen.redsquare

import android.content.Context
import com.google.gson.JsonArray
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File

private fun processFixturePartPatterns(names:Set<String>)=names.filter{
    (it.startsWith("chat.")||it.startsWith("groupchat."))&&it.endsWith(".v1")
}.map{Regex("${Regex.escape(it)}\\.part\\.[0-9a-f]{64}(?:\\.bak|\\.new)?")}

/** Expand only canonical encrypted parts belonging to the fixture's exact private/group roots. */
internal fun processFixtureFiles(context:Context,names:Set<String>):Set<String> = synchronized(SaitoLocalState.lock) {
    check(names.all{File(it).name==it})
    val parts=processFixturePartPatterns(names)
    names+requireNotNull(context.noBackupFilesDir.listFiles()).map{it.name}.filter{name->parts.any{it.matches(name)}}
}

/** Exact allowlists stay with each fixture; encrypted installation snapshots stay device-only. */
internal fun snapshotProcessFixtureFiles(context:Context,directory:File,names:Set<String>):JsonArray =
    synchronized(SaitoLocalState.lock){
        check(directory.parentFile==context.noBackupFilesDir&&names.all{File(it).name==it})
        check(directory.mkdir())
        JsonArray().apply{processFixtureFiles(context,names).forEach{name->
            val file=File(context.noBackupFilesDir,name)
            if(file.exists()){check(file.isFile);file.copyTo(File(directory,name));add(name)}
        }}
    }

internal fun restoreProcessFixtureFiles(context:Context,directory:File,names:Set<String>,saved:JsonArray) =
    synchronized(SaitoLocalState.lock){
        check(directory.parentFile==context.noBackupFilesDir&&names.all{File(it).name==it})
        val retained=saved.map{it.asString}
        val parts=processFixturePartPatterns(names)
        check(retained.distinct().size==retained.size&&retained.all{name->
            (name in names||parts.any{it.matches(name)})&&File(directory,name).isFile})
        processFixtureFiles(context,names).forEach{name->val file=File(context.noBackupFilesDir,name);check(!file.exists()||file.delete())}
        retained.forEach{name->File(directory,name).copyTo(File(context.noBackupFilesDir,name))}
    }
