package nl.davidengelen.redsquare

import android.content.Context
import com.google.gson.JsonArray
import nl.davidengelen.redsquare.saito.SaitoLocalState
import java.io.File

/** Exact allowlists stay with each fixture; encrypted installation snapshots stay device-only. */
internal fun snapshotProcessFixtureFiles(context:Context,directory:File,names:Set<String>):JsonArray =
    synchronized(SaitoLocalState.lock){
        check(directory.parentFile==context.noBackupFilesDir&&names.all{File(it).name==it})
        check(directory.mkdir())
        JsonArray().apply{names.forEach{name->
            val file=File(context.noBackupFilesDir,name)
            if(file.exists()){check(file.isFile);file.copyTo(File(directory,name));add(name)}
        }}
    }

internal fun restoreProcessFixtureFiles(context:Context,directory:File,names:Set<String>,saved:JsonArray) =
    synchronized(SaitoLocalState.lock){
        check(directory.parentFile==context.noBackupFilesDir&&names.all{File(it).name==it})
        val retained=saved.map{it.asString}
        check(retained.distinct().size==retained.size&&retained.all{it in names&&File(directory,it).isFile})
        names.forEach{name->val file=File(context.noBackupFilesDir,name);check(!file.exists()||file.delete())}
        retained.forEach{name->File(directory,name).copyTo(File(context.noBackupFilesDir,name))}
    }
